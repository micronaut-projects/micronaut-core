/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.Introduction;
import io.micronaut.aop.internal.intercepted.InterceptedMethodUtil;
import io.micronaut.aop.runtime.RuntimeProxy;
import io.micronaut.aop.writer.AopProxyWriter;
import io.micronaut.aop.writer.ProxyingBeanDefinitionWriter;
import io.micronaut.aop.writer.RuntimeProxyBeanDefinitionWriter;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.bean.definition.builder.ConstructorDefinition;
import io.micronaut.context.bean.definition.builder.FieldDefinition;
import io.micronaut.context.bean.definition.builder.MemberDefinition;
import io.micronaut.context.bean.definition.builder.MethodDefinition;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.utils.BeanInjectionUtils;
import io.micronaut.inject.validation.RequiresValidation;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionWriter;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING_ARRAY;

/**
 * Default {@link ElementBeanDefinitionBuilderFactory} implementation backed by {@link io.micronaut.inject.writer.BeanDefinitionWriter}.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
public class DefaultElementBeanDefinitionBuilderFactory implements ElementBeanDefinitionBuilderFactory<OutputObjectDef> {
    private static final String ANN_VALIDATED = "io.micronaut.validation.Validated";
    private static final String ANN_REQUIRES_VALIDATION = RequiresValidation.class.getName();

    private final VisitorContext visitorContext;
    private int uniqueIdentifier;

    /**
     * @param visitorContext The visitor context backing code generation
     */
    public DefaultElementBeanDefinitionBuilderFactory(VisitorContext visitorContext) {
        this.visitorContext = visitorContext;
    }

    @Override
    public ElementBeanDefinitionBuilder<OutputObjectDef> ofType(ClassElement classElement) {
        MethodElement constructorElement = classElement.getPrimaryConstructor().orElse(null);
        if (constructorElement != null) {
            return constructor(
                BeanInjectionUtils.createConstructorDefinition(constructorElement, visitorContext)
            );
        } else {
            MethodElement defaultConstructor = MethodElement.of(
                classElement,
                AnnotationMetadata.EMPTY_METADATA,
                classElement,
                classElement,
                "<init>"
            );

            return constructor(
                BeanInjectionUtils.createConstructorDefinition(defaultConstructor, visitorContext)
            );
        }
    }

    @Override
    public ElementBeanDefinitionBuilder<OutputObjectDef> factoryMethod(MethodElement methodElement) {
        return factoryMethod(BeanInjectionUtils.createMethodDefinition(
            methodElement.getGenericReturnType(),
            methodElement,
            methodElement,
            methodElement.isReflectionRequired(),
            visitorContext
        ));
    }

    @Override
    public ElementBeanDefinitionBuilder<OutputObjectDef> factoryField(FieldElement fieldElement) {
        return factoryField(BeanInjectionUtils.createFieldDefinition(
            fieldElement.getGenericType(),
            fieldElement,
            fieldElement.isReflectionRequired(),
            visitorContext
        ));
    }

    @Override
    public ElementBeanDefinitionBuilder<OutputObjectDef> constructor(ConstructorDefinition<ClassElement, MethodElement> constructorDefinition) {
        return new BeanDefinitionWriter(constructorDefinition, visitorContext);
    }

    @Override
    public ElementBeanDefinitionBuilder<OutputObjectDef> constructor(ConstructorDefinition<ClassElement, MethodElement> constructorDefinition,
                                                                     @Nullable String beanDefinitionName,
                                                                     @Nullable AnnotationMetadata annotationMetadata) {
        return new BeanDefinitionWriter(constructorDefinition, beanDefinitionName, annotationMetadata, visitorContext);
    }

    @Override
    public ElementBeanDefinitionBuilder<OutputObjectDef> factoryMethod(MethodDefinition<ClassElement, MethodElement> methodDefinition) {
        return new BeanDefinitionWriter(methodDefinition, visitorContext, uniqueIdentifier++);
    }

    @Override
    public ElementBeanDefinitionBuilder<OutputObjectDef> factoryField(FieldDefinition<ClassElement, FieldElement> fieldDefinition) {
        return new BeanDefinitionWriter(fieldDefinition, visitorContext);
    }

    @Override
    public ElementProxyBuilder<OutputObjectDef> aroundProxy(ClassElement targetType,
                                                            AnnotationMetadata aopElementAnnotationProcessor,
                                                            ElementBeanDefinitionBuilder<OutputObjectDef> targetBeanDefinitionBuilder) {

        if (targetType.isFinal()) {
            throw new ProcessingException(targetType, "Cannot apply AOP advice to final class. Class must be made non-final to support proxying: " + targetType.getName());
        }
        BeanDefinitionWriter targetBeanWriter = (BeanDefinitionWriter) targetBeanDefinitionBuilder;
        MemberDefinition<ClassElement> elementProducerDefinition = targetBeanWriter.getElementProducerDefinition();
        boolean isFactoryMethod = !(elementProducerDefinition instanceof ConstructorDefinition<ClassElement, ?>);

        Map<CharSequence, Boolean> settings = new LinkedHashMap<>();
        OptionalValues<Boolean> aroundSettings = aopElementAnnotationProcessor.getValues(AnnotationUtil.ANN_AROUND, Boolean.class);
        aroundSettings.forEach(settings::put);
        if (isFactoryMethod) {
            settings.put(Interceptor.PROXY_TARGET, true);
        }
        aroundSettings = OptionalValues.of(Boolean.class, settings);

        ProxyingBeanDefinitionWriter visitor;

        AnnotationValue<?>[] interceptorBinding = InterceptedMethodUtil.resolveInterceptorBinding(aopElementAnnotationProcessor, InterceptorKind.AROUND);
        if (targetType.hasStereotype(RuntimeProxy.class)) {
            RuntimeProxyBeanDefinitionWriter runtimeProxyBeanDefinitionWriter = new RuntimeProxyBeanDefinitionWriter(
                targetType,
                targetBeanWriter,
                aroundSettings,
                visitorContext,
                interceptorBinding
            );
            BeanDefinitionWriter beanDefinitionWriter = (BeanDefinitionWriter) runtimeProxyBeanDefinitionWriter.beanDefinitionBuilder();
            if (isFactoryMethod) {
                beanDefinitionWriter.visitSuperBeanDefinitionFactory(targetBeanWriter.getBeanDefinitionName());
            } else {
                beanDefinitionWriter.visitSuperBeanDefinition(targetBeanWriter.getBeanDefinitionName());
            }
            visitor = runtimeProxyBeanDefinitionWriter;
        } else {
            AopProxyWriter aopProxyWriter = new AopProxyWriter(
                targetType,
                targetBeanWriter,
                aroundSettings,
                visitorContext,
                interceptorBinding
            );
            BeanDefinitionWriter beanDefinitionWriter = (BeanDefinitionWriter) aopProxyWriter.beanDefinitionBuilder();
            if (isFactoryMethod) {
                beanDefinitionWriter.visitSuperBeanDefinitionFactory(targetBeanWriter.getBeanDefinitionName());
            } else {
                beanDefinitionWriter.visitSuperBeanDefinition(targetBeanWriter.getBeanDefinitionName());
            }
            visitor = aopProxyWriter;
        }
        return visitor;
    }

    @Override
    public ElementProxyBuilder<OutputObjectDef> introductionProxy(ClassElement target) {
        AnnotationMetadata annotationMetadata = target.getAnnotationMetadata();

        List<ClassElement> interfaceTypes = Arrays.stream(annotationMetadata.getValue(Introduction.class, "interfaces", String[].class).orElse(EMPTY_STRING_ARRAY))
            .map(v -> visitorContext.getClassElement(v, visitorContext.getElementAnnotationMetadataFactory().readOnly())
                .orElseThrow(() -> new ProcessingException(target, "Cannot find interface: " + v))
            ).toList();

        ProxyingBeanDefinitionWriter proxyBuilder = introductionProxy(target, true);

        interfaceTypes.forEach(proxyBuilder::implementInterface);

        return proxyBuilder;
    }

    @Override
    public ElementProxyBuilder<OutputObjectDef> introductionProxy(String proxyName, AnnotationMetadata proxyAnnotationMetadata) {
        return introductionProxy(ClassElement.of(proxyName, true, proxyAnnotationMetadata, Map.of()), null, false);
    }

    @Override
    public ElementProxyBuilder<OutputObjectDef> introductionProxy(String proxyName, AnnotationMetadata proxyAnnotationMetadata, ClassElement beanType) {
        return introductionProxy(ClassElement.of(proxyName, true, proxyAnnotationMetadata, Map.of()), beanType, false);
    }

    /**
     * Creates an introduction proxy writer for the specified target.
     *
     * @param target               The element being proxied
     * @param implementsInterface  Whether the proxy implements the target as an interface
     * @return The configured proxy writer
     */
    private ProxyingBeanDefinitionWriter introductionProxy(ClassElement target,
                                                           boolean implementsInterface) {
        return introductionProxy(target, null, implementsInterface);
    }

    private ProxyingBeanDefinitionWriter introductionProxy(ClassElement target,
                                                           @Nullable ClassElement beanType,
                                                           boolean implementsInterface) {
        AnnotationMetadata annotationMetadata = target.getAnnotationMetadata();

        io.micronaut.core.annotation.AnnotationValue<?>[] aroundInterceptors =
            InterceptedMethodUtil.resolveInterceptorBinding(annotationMetadata, InterceptorKind.AROUND);
        io.micronaut.core.annotation.AnnotationValue<?>[] introductionInterceptors = InterceptedMethodUtil.resolveInterceptorBinding(annotationMetadata, InterceptorKind.INTRODUCTION);

        io.micronaut.core.annotation.AnnotationValue<?>[] interceptorTypes = ArrayUtils.concat(aroundInterceptors, introductionInterceptors);

        ProxyingBeanDefinitionWriter aopProxyWriter;

        if (target.hasStereotype(RuntimeProxy.class)) {
            if (beanType == null) {
                aopProxyWriter = new RuntimeProxyBeanDefinitionWriter(
                    target,
                    implementsInterface,
                    visitorContext,
                    interceptorTypes);
            } else {
                aopProxyWriter = new RuntimeProxyBeanDefinitionWriter(
                    target,
                    beanType,
                    implementsInterface,
                    visitorContext,
                    interceptorTypes);
            }

        } else {
            aopProxyWriter = new AopProxyWriter(
                target,
                implementsInterface,
                visitorContext,
                interceptorTypes);
        }

        // Because we add validated interceptor in some cases, this needs to run before the constructor visit
        if (target.hasAnnotation(ANN_REQUIRES_VALIDATION)) {
            if (target.hasStereotype(ConfigurationReader.class)) {
                BeanDefinitionWriter beanDefinitionWriter = (BeanDefinitionWriter) aopProxyWriter.beanDefinitionBuilder();
                // Configuration beans are validated at the startup and don't require validation advice
                beanDefinitionWriter.setValidated(true);
            } else {
                for (MethodElement methodElement : target.getEnclosedElements(ElementQuery.ALL_METHODS.annotated(am -> am.hasAnnotation(ANN_REQUIRES_VALIDATION)))) {
                    methodElement.annotate(ANN_VALIDATED);
                }
            }
        }

        return aopProxyWriter;
    }

}
