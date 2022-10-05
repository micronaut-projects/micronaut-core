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
package io.micronaut.aop.writer;

import io.micronaut.aop.Adapter;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.Introduction;
import io.micronaut.aop.internal.intercepted.InterceptedMethodUtil;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NextMajorVersion;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.inject.ProcessingException;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.processing.AopHelper;
import io.micronaut.inject.processing.JavaModelUtils;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionVisitor;
import io.micronaut.inject.writer.BeanDefinitionWriter;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AOP helper to connect Inject module with AOP.
 */
@Internal
@NextMajorVersion("Correct project dependency so this hack is not needed")
public class AopHelperImpl implements AopHelper {

    @Override
    public BeanDefinitionVisitor visitAdaptedMethod(ClassElement classElement,
                                                    MethodElement sourceMethod,
                                                    AtomicInteger adaptedMethodIndex,
                                                    VisitorContext visitorContext) {

        AnnotationMetadata methodAnnotationMetadata = sourceMethod.getDeclaredMetadata();

        Optional<ClassElement> interfaceToAdaptValue = methodAnnotationMetadata.getValue(Adapter.class, String.class)
            .flatMap(clazz -> visitorContext.getClassElement(clazz, visitorContext.getElementAnnotationMetadataFactory().readOnly()));

        if (!interfaceToAdaptValue.isPresent()) {
            return null;
        }
        ClassElement interfaceToAdapt = interfaceToAdaptValue.get();
        if (!interfaceToAdapt.isInterface()) {
            throw new ProcessingException(sourceMethod, "Class to adapt [" + interfaceToAdapt.getName() + "] is not an interface");
        }

        String rootName = classElement.getSimpleName() + '$' + interfaceToAdapt.getSimpleName() + '$' + sourceMethod.getSimpleName();
        String beanClassName = rootName + adaptedMethodIndex.incrementAndGet();

        AopProxyWriter aopProxyWriter = new AopProxyWriter(
            classElement.getPackageName(),
            beanClassName,
            true,
            false,
            sourceMethod,
            new AnnotationMetadataHierarchy(classElement.getAnnotationMetadata(), methodAnnotationMetadata),
            new ClassElement[]{interfaceToAdapt},
            visitorContext
        );

        aopProxyWriter.visitDefaultConstructor(methodAnnotationMetadata, visitorContext);

        List<MethodElement> methods = interfaceToAdapt.getEnclosedElements(ElementQuery.ALL_METHODS.onlyAbstract());
        if (methods.isEmpty()) {
            throw new ProcessingException(sourceMethod, "Interface to adapt [" + interfaceToAdapt.getName() + "] is not a SAM type. No methods found.");
        }
        if (methods.size() > 1) {
            throw new ProcessingException(sourceMethod, "Interface to adapt [" + interfaceToAdapt.getName() + "] is not a SAM type. More than one abstract method declared.");
        }

        MethodElement targetMethod = methods.iterator().next();

        ParameterElement[] sourceParams = sourceMethod.getParameters();
        ParameterElement[] targetParams = targetMethod.getParameters();

        int paramLen = targetParams.length;
        if (paramLen != sourceParams.length) {
            throw new ProcessingException(sourceMethod, "Cannot adapt method [" + sourceMethod + "] to target method [" + targetMethod + "]. Argument lengths don't match.");
        }
        if (sourceMethod.isSuspend()) {
            throw new ProcessingException(sourceMethod, "Cannot adapt method [" + sourceMethod + "] to target method [" + targetMethod + "]. Kotlin suspend method not supported here.");
        }

        Map<String, ClassElement> typeVariables = interfaceToAdapt.getTypeArguments();
        Map<String, ClassElement> genericTypes = new LinkedHashMap<>(paramLen);
        for (int i = 0; i < paramLen; i++) {
            ParameterElement targetParam = targetParams[i];
            ParameterElement sourceParam = sourceParams[i];

            ClassElement targetType = targetParam.getType();
            ClassElement targetGenericType = targetParam.getGenericType();
            ClassElement sourceType = sourceParam.getGenericType();

            // ??? Java returns generic placeholder for the generic type and Groovy from the ordinary type
            if (targetGenericType instanceof GenericPlaceholderElement) {
                GenericPlaceholderElement genericPlaceholderElement = (GenericPlaceholderElement) targetGenericType;
                String variableName = genericPlaceholderElement.getVariableName();
                if (typeVariables.containsKey(variableName)) {
                    genericTypes.put(variableName, sourceType);
                }
            } else if (targetType instanceof GenericPlaceholderElement) {
                GenericPlaceholderElement genericPlaceholderElement = (GenericPlaceholderElement) targetType;
                String variableName = genericPlaceholderElement.getVariableName();
                if (typeVariables.containsKey(variableName)) {
                    genericTypes.put(variableName, sourceType);
                }
            }

            if (!sourceType.isAssignable(targetGenericType.getName())) {
                throw new ProcessingException(sourceMethod, "Cannot adapt method [" + sourceMethod + "] to target method [" + targetMethod + "]. Type [" + sourceType.getName() + "] is not a subtype of type [" + targetGenericType.getName() + "] for argument at position " + i);
            }
        }

        if (!genericTypes.isEmpty()) {
            aopProxyWriter.visitTypeArguments(Collections.singletonMap(interfaceToAdapt.getName(), genericTypes));
        }

        AnnotationClassValue<?>[] adaptedArgumentTypes = Arrays.stream(sourceParams)
            .map(p -> new AnnotationClassValue<>(JavaModelUtils.getClassname(p.getGenericType())))
            .toArray(AnnotationClassValue[]::new);

        targetMethod = targetMethod.withNewOwningType(classElement);

        targetMethod.annotate(Adapter.class, builder -> {
            builder.member(Adapter.InternalAttributes.ADAPTED_BEAN, new AnnotationClassValue<>(JavaModelUtils.getClassname(classElement)));
            builder.member(Adapter.InternalAttributes.ADAPTED_METHOD, sourceMethod.getName());
            builder.member(Adapter.InternalAttributes.ADAPTED_ARGUMENT_TYPES, adaptedArgumentTypes);
            String qualifier = classElement.stringValue(AnnotationUtil.NAMED).orElse(null);
            if (StringUtils.isNotEmpty(qualifier)) {
                builder.member(Adapter.InternalAttributes.ADAPTED_QUALIFIER, qualifier);
            }
        });

        aopProxyWriter.visitAroundMethod(interfaceToAdapt, targetMethod);

        return aopProxyWriter;
    }

    @Override
    public boolean visitIntrospectedMethod(BeanDefinitionVisitor visitor, ClassElement typeElement, MethodElement methodElement) {
        AopProxyWriter aopProxyWriter = (AopProxyWriter) visitor;

        final AnnotationMetadata resolvedTypeMetadata = typeElement.getAnnotationMetadata();
        final boolean resolvedTypeMetadataIsAopProxyType = InterceptedMethodUtil.hasDeclaredAroundAdvice(resolvedTypeMetadata);

        if (methodElement.isAbstract()
            || resolvedTypeMetadataIsAopProxyType
            || InterceptedMethodUtil.hasDeclaredAroundAdvice(methodElement.getAnnotationMetadata())) {
            addToIntroduction(aopProxyWriter, typeElement, methodElement, false);
            return true;
        }
        return false;
    }

    @Override
    public AopProxyWriter createIntroductionAopProxyWriter(ClassElement typeElement,
                                                           VisitorContext visitorContext) {
        AnnotationMetadata annotationMetadata = typeElement.getAnnotationMetadata();

        String packageName = typeElement.getPackageName();
        String beanClassName = typeElement.getSimpleName();
        io.micronaut.core.annotation.AnnotationValue<?>[] aroundInterceptors =
            InterceptedMethodUtil.resolveInterceptorBinding(annotationMetadata, InterceptorKind.AROUND);
        io.micronaut.core.annotation.AnnotationValue<?>[] introductionInterceptors = InterceptedMethodUtil.resolveInterceptorBinding(annotationMetadata, InterceptorKind.INTRODUCTION);

        ClassElement[] interfaceTypes = Arrays.stream(annotationMetadata.getValue(Introduction.class, "interfaces", String[].class).orElse(new String[0]))
            .map(v -> visitorContext.getClassElement(v, visitorContext.getElementAnnotationMetadataFactory().readOnly())
                .orElseThrow(() -> new ProcessingException(typeElement, "Cannot find interface: " + v))
            ).toArray(ClassElement[]::new);

        io.micronaut.core.annotation.AnnotationValue<?>[] interceptorTypes = ArrayUtils.concat(aroundInterceptors, introductionInterceptors);
        boolean isInterface = typeElement.isInterface();
        AopProxyWriter aopProxyWriter = new AopProxyWriter(
            packageName,
            beanClassName,
            isInterface,
            typeElement,
            annotationMetadata,
            interfaceTypes,
            visitorContext,
            interceptorTypes);

        Arrays.stream(interfaceTypes)
            .flatMap(interfaceElement -> interfaceElement.getEnclosedElements(ElementQuery.ALL_METHODS).stream())
            .forEach(methodElement -> addToIntroduction(aopProxyWriter, typeElement, methodElement.withNewOwningType(typeElement), true));

        return aopProxyWriter;
    }

    @Override
    public AopProxyWriter createAroundAopProxyWriter(BeanDefinitionVisitor existingWriter,
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

        return new AopProxyWriter(
            (BeanDefinitionWriter) existingWriter,
            aroundSettings,
            visitorContext,
            InterceptedMethodUtil.resolveInterceptorBinding(aopElementAnnotationProcessor, InterceptorKind.AROUND)
        );
    }

    private static void addToIntroduction(AopProxyWriter aopProxyWriter,
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

    @Override
    public void visitAroundMethod(BeanDefinitionVisitor existingWriter, TypedElement beanType, MethodElement methodElement) {
        AopProxyWriter aopProxyWriter = (AopProxyWriter) existingWriter;
        aopProxyWriter.visitInterceptorBinding(
            InterceptedMethodUtil.resolveInterceptorBinding(methodElement.getAnnotationMetadata(), InterceptorKind.AROUND)
        );
        aopProxyWriter.visitAroundMethod(beanType, methodElement);
    }
}
