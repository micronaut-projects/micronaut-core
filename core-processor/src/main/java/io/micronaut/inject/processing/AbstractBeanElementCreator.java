/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.inject.processing;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.Introduction;
import io.micronaut.aop.runtime.RuntimeProxy;
import io.micronaut.aop.internal.intercepted.InterceptedMethodUtil;
import io.micronaut.aop.writer.AopProxyWriter;
import io.micronaut.aop.writer.RuntimeProxyBeanDefinitionWriter;
import io.micronaut.context.RequiresCondition;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.validation.RequiresValidation;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionVisitor;
import io.micronaut.inject.writer.BeanDefinitionWriter;
import io.micronaut.inject.writer.ProxyingBeanDefinitionVisitor;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING_ARRAY;

/**
 * Abstract shared functionality of the builder.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
abstract class AbstractBeanElementCreator implements BeanDefinitionCreator {

    public static final String ANN_VALIDATED = "io.micronaut.validation.Validated";
    protected static final String ANN_REQUIRES_VALIDATION = RequiresValidation.class.getName();

    protected final ClassElement classElement;
    protected final VisitorContext visitorContext;
    protected final List<BeanDefinitionVisitor> beanDefinitionWriters = new LinkedList<>();

    protected AbstractBeanElementCreator(ClassElement classElement, VisitorContext visitorContext) {
        this.classElement = classElement;
        this.visitorContext = visitorContext;
        checkPackage(classElement);
    }

    @Override
    public final Collection<BeanDefinitionVisitor> build() {
        buildInternal();
        return beanDefinitionWriters;
    }

    /**
     * Build visitors.
     */
    protected abstract void buildInternal();

    private void checkPackage(ClassElement classElement) {
        io.micronaut.inject.ast.PackageElement packageElement = classElement.getPackage();
        if (packageElement.isUnnamed()) {
            throw new ProcessingException(classElement, "Micronaut beans cannot be in the default package");
        }
    }

    protected void visitAnnotationMetadata(BeanDefinitionVisitor writer, AnnotationMetadata annotationMetadata) {
        for (io.micronaut.core.annotation.AnnotationValue<Requires> annotation : annotationMetadata.getAnnotationValuesByType(Requires.class)) {
            annotation.stringValue(RequiresCondition.MEMBER_BEAN_PROPERTY)
                .ifPresent(beanProperty -> {
                    annotation.stringValue(RequiresCondition.MEMBER_BEAN)
                        .flatMap(className -> visitorContext.getClassElement(className, visitorContext.getElementAnnotationMetadataFactory().readOnly()))
                        .ifPresent(classElement -> {
                            String requiredValue = annotation.stringValue().orElse(null);
                            String notEqualsValue = annotation.stringValue(RequiresCondition.MEMBER_NOT_EQUALS).orElse(null);
                            writer.visitAnnotationMemberPropertyInjectionPoint(classElement, beanProperty, requiredValue, notEqualsValue);
                        });
                });
        }
    }

    public static AnnotationMetadata getElementAnnotationMetadata(MemberElement memberElement) {
        if (memberElement instanceof MethodElement methodElement) {
            return methodElement.getMethodAnnotationMetadata();
        }
        return memberElement.getAnnotationMetadata();
    }

    protected boolean visitIntrospectedMethod(BeanDefinitionVisitor visitor, ClassElement classElement, MethodElement methodElement) {
        ProxyingBeanDefinitionVisitor aopProxyWriter = (ProxyingBeanDefinitionVisitor) visitor;

        final AnnotationMetadata resolvedTypeMetadata = classElement.getAnnotationMetadata();
        final boolean resolvedTypeMetadataIsAopProxyType = InterceptedMethodUtil.hasDeclaredAroundAdvice(resolvedTypeMetadata);

        if (methodElement.isAbstract()
            || resolvedTypeMetadataIsAopProxyType
            || InterceptedMethodUtil.hasDeclaredAroundAdvice(methodElement.getAnnotationMetadata())) {
            addToIntroduction(aopProxyWriter, classElement, methodElement, false);
            return true;
        } else if (methodElement.hasDeclaredStereotype(Executable.class)) {
            aopProxyWriter.visitExecutableMethod(
                classElement,
                methodElement,
                visitorContext
            );
        }
        return false;
    }

    protected static void addToIntroduction(ProxyingBeanDefinitionVisitor aopProxyWriter,
                                            ClassElement classElement,
                                            MethodElement methodElement,
                                            boolean ignoreNotAbstract) {
        AnnotationMetadata methodAnnotationMetadata = methodElement.getDeclaredMetadata();

        if (InterceptedMethodUtil.hasAroundStereotype(methodAnnotationMetadata)) {
            aopProxyWriter.visitInterceptorBinding(
                InterceptedMethodUtil.resolveInterceptorBinding(methodAnnotationMetadata, InterceptorKind.AROUND)
            );
        }

        if (!classElement.getName().equals(methodElement.getDeclaringType().getName())) {
            aopProxyWriter.addOriginatingElement(methodElement.getDeclaringType());
        }

        ClassElement declaringType = methodElement.getDeclaringType();
        if (methodElement.isAbstract()) {
            aopProxyWriter.visitIntroductionMethod(declaringType, methodElement);
        } else if (!ignoreNotAbstract) {
            boolean isInterface = methodElement.getDeclaringType().isInterface();
            boolean isDefault = methodElement.isDefault();
            if (isInterface && isDefault) {
                // Default methods cannot be "super" accessed on the defined type
                declaringType = classElement;
            }
            // only apply around advise to non-abstract methods of introduction advise
            aopProxyWriter.visitAroundMethod(declaringType, methodElement);
        }
    }

    protected ProxyingBeanDefinitionVisitor createAroundAopProxyWriter(ClassElement targetType,
                                                                       BeanDefinitionVisitor existingWriter,
                                                                       AnnotationMetadata aopElementAnnotationProcessor,
                                                                       VisitorContext visitorContext,
                                                                       boolean forceProxyTarget) {
        OptionalValues<Boolean> aroundSettings = aopElementAnnotationProcessor.getValues(AnnotationUtil.ANN_AROUND, Boolean.class);
        Map<CharSequence, Boolean> settings = new LinkedHashMap<>();
        for (CharSequence setting : aroundSettings) {
            Optional<Boolean> entry = aroundSettings.get(setting);
            entry.ifPresent(val -> settings.put(setting, val));
        }
        if (forceProxyTarget) {
            settings.put(Interceptor.PROXY_TARGET, true);
        }
        aroundSettings = OptionalValues.of(Boolean.class, settings);

        if (targetType.hasStereotype(RuntimeProxy.class)) {
            return new RuntimeProxyBeanDefinitionWriter(
                targetType,
                (BeanDefinitionWriter) existingWriter,
                aroundSettings,
                visitorContext,
                InterceptedMethodUtil.resolveInterceptorBinding(aopElementAnnotationProcessor, InterceptorKind.AROUND)
            );
        }

        return new AopProxyWriter(
            targetType,
            (BeanDefinitionWriter) existingWriter,
            aroundSettings,
            visitorContext,
            InterceptedMethodUtil.resolveInterceptorBinding(aopElementAnnotationProcessor, InterceptorKind.AROUND)
        );
    }

    protected ProxyingBeanDefinitionVisitor createIntroductionAopProxyWriter(ClassElement targetType,
                                                                             VisitorContext visitorContext) {
        AnnotationMetadata annotationMetadata = targetType.getAnnotationMetadata();

        io.micronaut.core.annotation.AnnotationValue<?>[] aroundInterceptors =
            InterceptedMethodUtil.resolveInterceptorBinding(annotationMetadata, InterceptorKind.AROUND);
        io.micronaut.core.annotation.AnnotationValue<?>[] introductionInterceptors = InterceptedMethodUtil.resolveInterceptorBinding(annotationMetadata, InterceptorKind.INTRODUCTION);

        ClassElement[] interfaceTypes = Arrays.stream(annotationMetadata.getValue(Introduction.class, "interfaces", String[].class).orElse(EMPTY_STRING_ARRAY))
            .map(v -> visitorContext.getClassElement(v, visitorContext.getElementAnnotationMetadataFactory().readOnly())
                .orElseThrow(() -> new ProcessingException(targetType, "Cannot find interface: " + v))
            ).toArray(ClassElement[]::new);

        io.micronaut.core.annotation.AnnotationValue<?>[] interceptorTypes = ArrayUtils.concat(aroundInterceptors, introductionInterceptors);

        ProxyingBeanDefinitionVisitor aopProxyWriter;

        if (targetType.hasStereotype(RuntimeProxy.class)) {
            aopProxyWriter = new RuntimeProxyBeanDefinitionWriter(
                targetType,
                interfaceTypes,
                visitorContext,
                interceptorTypes);

        } else {
            aopProxyWriter = new AopProxyWriter(
                targetType,
                interfaceTypes,
                visitorContext,
                interceptorTypes);
        }

        Arrays.stream(interfaceTypes)
            .flatMap(interfaceElement -> interfaceElement.getEnclosedElements(ElementQuery.ALL_METHODS).stream())
            .forEach(methodElement -> addToIntroduction(aopProxyWriter, targetType, methodElement.withNewOwningType(targetType), true));

        return aopProxyWriter;
    }

}
