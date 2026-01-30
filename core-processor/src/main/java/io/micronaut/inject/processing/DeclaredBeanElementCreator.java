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

import io.micronaut.aop.Adapter;
import io.micronaut.aop.internal.intercepted.InterceptedMethodUtil;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.bean.definition.builder.Builder;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NextMajorVersion;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ElementBeanDefinitionBuilder;
import io.micronaut.inject.ElementBeanDefinitionBuilderFactory;
import io.micronaut.inject.ElementProxyBuilder;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.validation.RequiresValidation;
import io.micronaut.inject.visitor.VisitorContext;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ordinary declared bean.
 *
 * @param <R> The builder result type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@NullUnmarked
@Internal
sealed class DeclaredBeanElementCreator<R> extends AbstractBeanElementCreator<R> permits AopIntroductionProxySupportedBeanElementCreator, ConfigurationReaderBeanElementCreator, FactoryBeanElementCreator {

    private static final String ANN_VALIDATED = "io.micronaut.validation.Validated";
    private static final String ANN_REQUIRES_VALIDATION = RequiresValidation.class.getName();

    private static final String MSG_ADAPTER_METHOD_PREFIX = "Cannot adapt method [";
    private static final String MSG_TARGET_METHOD_PREFIX = "] to target method [";

    protected final boolean isAopProxy;
    protected final List<Builder<List<R>>> additionalBuilders = new ArrayList<>();
    private final AtomicInteger adaptedMethodIndex = new AtomicInteger(0);
    private ElementProxyBuilder<R> aopProxyBuilder;

    protected DeclaredBeanElementCreator(ClassElement classElement, VisitorContext visitorContext, boolean isAopProxy, ElementBeanDefinitionBuilderFactory<R> beanDefinitionBuilderFactory) {
        super(classElement, visitorContext, beanDefinitionBuilderFactory);
        this.isAopProxy = isAopProxy;

        wantOfIncorrectUseOfExecutableMethodProcessor(classElement, visitorContext);
    }

    private void wantOfIncorrectUseOfExecutableMethodProcessor(ClassElement classElement, VisitorContext visitorContext) {
        Map<String, ClassElement> processor = classElement.getTypeArguments(ExecutableMethodProcessor.class);
        if (processor == null || processor.isEmpty()) {
            return;
        }
        ClassElement annotation = processor.get("A");
        if (annotation != null) {
            AnnotationValue<Executable> executable = annotation.getAnnotation(Executable.class);
            if (executable != null && executable.booleanValue(Executable.MEMBER_PROCESS_ON_STARTUP).orElse(false)) {
                return; // Correct ExecutableMethodProcessor should have @Executable(processOnStartup=true)
            }
        }
        String message = "ExecutableMethodProcessor is supposed to be used with an annotation that has @Executable(processOnStartup = true). In the future version this will be an error.";
        visitorContext.warn(
            message,
            classElement
        );
        classElement.annotate(Deprecated.class, builder -> builder.value(message));
    }

    @Override
    public final List<R> buildInternal() {
        ElementBeanDefinitionBuilder<R> beanDefinitionBuilder = createBeanDefinitionBuilder();
        if (isAopProxy) {
            // Always create AOP proxy
            getAopProxyBuilder(beanDefinitionBuilder, null);
        }
        build(beanDefinitionBuilder);
        List<R> result = new ArrayList<>(beanDefinitionBuilder.build());
        if (aopProxyBuilder != null) {
            result.addAll(aopProxyBuilder.build());
        }
        for (Builder<List<R>> additionalBuilder : additionalBuilders) {
            result.addAll(additionalBuilder.build());
        }
        return result;
    }

    /**
     * Create a bean definition visitor.
     *
     * @return the visitor
     */
    protected ElementBeanDefinitionBuilder<R> createBeanDefinitionBuilder() {
        return beanDefinitionBuilderFactory.ofType(classElement);
    }

    /**
     * Create an AOP proxy bean definition builder.
     *
     * @param targetBeanDefinitionBuilder the builder of the current bean definition
     * @param methodElement               the method that is originating the AOP proxy
     * @return The AOP proxy bean definition builder
     */
    protected ElementProxyBuilder<R> getAopProxyBuilder(ElementBeanDefinitionBuilder<R> targetBeanDefinitionBuilder, @Nullable MethodElement methodElement) {
        if (aopProxyBuilder == null) {
            AnnotationMetadata annotationMetadata = isAopProxy || methodElement == null ? classElement.getAnnotationMetadata() : methodElement.getAnnotationMetadata();
            aopProxyBuilder = beanDefinitionBuilderFactory.aroundProxy(classElement, annotationMetadata, targetBeanDefinitionBuilder);
        }
        return aopProxyBuilder;
    }

    /**
     * @return true if the class should be processed as a properties bean
     */
    protected boolean processAsProperties() {
        return false;
    }

    protected void build(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder) {
        Set<FieldElement> processedFields = new HashSet<>();
        ElementQuery<MemberElement> memberQuery = ElementQuery.ALL_FIELD_AND_METHODS.includeHiddenElements();
        if (processAsProperties()) {
            memberQuery = memberQuery.excludePropertyElements();
            for (PropertyElement propertyElement : classElement.getBeanProperties()) {
                if (visitPropertyInternal(beanDefinitionBuilder, propertyElement)) {
                    propertyElement.getField().ifPresent(processedFields::add);
                }
            }
        } else {
            for (PropertyElement propertyElement : classElement.getSyntheticBeanProperties()) {
                if (visitPropertyInternal(beanDefinitionBuilder, propertyElement)) {
                    propertyElement.getField().ifPresent(processedFields::add);
                }
            }
        }
        List<MemberElement> memberElements = new ArrayList<>(classElement.getEnclosedElements(memberQuery));
        memberElements.removeAll(processedFields);
        for (MemberElement memberElement : memberElements) {
            if (memberElement instanceof FieldElement fieldElement) {
                visitFieldInternal(beanDefinitionBuilder, fieldElement);
            } else if (memberElement instanceof MethodElement methodElement) {
                visitMethodInternal(beanDefinitionBuilder, methodElement);
            } else if (!(memberElement instanceof PropertyElement)) {
                throw new IllegalStateException("Unknown element");
            }
        }
    }

    private void visitFieldInternal(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, FieldElement fieldElement) {
        boolean claimed = visitField(beanDefinitionBuilder, fieldElement);
        if (claimed) {
            addOriginatingElementIfNecessary(beanDefinitionBuilder, fieldElement);
        }
    }

    private void visitMethodInternal(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, MethodElement methodElement) {
        makeInterceptedForValidationIfNeeded(methodElement);
        boolean claimed = visitMethod(beanDefinitionBuilder, methodElement);
        if (claimed) {
            addOriginatingElementIfNecessary(beanDefinitionBuilder, methodElement);
        }
    }

    private boolean visitPropertyInternal(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, PropertyElement propertyElement) {
        boolean claimed = visitProperty(beanDefinitionBuilder, propertyElement);
        if (claimed) {
            propertyElement.getReadMethod().ifPresent(element -> addOriginatingElementIfNecessary(beanDefinitionBuilder, element));
            propertyElement.getWriteMethod().ifPresent(element -> addOriginatingElementIfNecessary(beanDefinitionBuilder, element));
            propertyElement.getField().ifPresent(element -> addOriginatingElementIfNecessary(beanDefinitionBuilder, element));
        }
        return claimed;
    }

    /**
     * Visit a property.
     *
     * @param beanDefinitionBuilder The bean definition builder
     * @param propertyElement       The property
     * @return true if processed
     */
    protected boolean visitProperty(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, PropertyElement propertyElement) {
        boolean claimed = false;
        Optional<? extends MemberElement> writeMember = propertyElement.getWriteMember();
        if (writeMember.isPresent()) {
            claimed |= visitPropertyWriteElement(beanDefinitionBuilder, propertyElement, writeMember.get());
        }
        Optional<? extends MemberElement> readMember = propertyElement.getReadMember();
        if (readMember.isPresent()) {
            boolean readElementClaimed = visitPropertyReadElement(beanDefinitionBuilder, propertyElement, readMember.get());
            claimed |= readElementClaimed;
        }
        // Process property's field if no methods were processed
        Optional<FieldElement> field = propertyElement.getField();
        if (!claimed && field.isPresent()) {
            FieldElement writeElement = field.get();
            claimed = visitField(beanDefinitionBuilder, writeElement);
        }
        return claimed;
    }

    /**
     * Visit a property read element.
     *
     * @param beanDefinitionBuilder The bean definition builder
     * @param propertyElement       The property
     * @param readElement           The read element
     * @return true if processed
     */
    protected boolean visitPropertyReadElement(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder,
                                               PropertyElement propertyElement,
                                               MemberElement readElement) {
        if (readElement instanceof MethodElement methodReadElement) {
            return visitPropertyReadElement(beanDefinitionBuilder, propertyElement, methodReadElement);
        }
        return false;
    }

    /**
     * Makes the method intercepted by the validation advice.
     * @param element The method element
     */
    protected void makeInterceptedForValidationIfNeeded(MethodElement element) {
        // The method with constrains should be intercepted with the validation interceptor
        if (element.hasDeclaredAnnotation(ANN_REQUIRES_VALIDATION)) {
            element.annotate(ANN_VALIDATED);
        }
    }

    /**
     * Visit a property method read element.
     *
     * @param beanDefinitionBuilder The bean definition builder
     * @param propertyElement       The property
     * @param readElement           The read element
     * @return true if processed
     */
    protected boolean visitPropertyReadElement(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder,
                                               PropertyElement propertyElement,
                                               MethodElement readElement) {
        return visitAopAndExecutableMethod(beanDefinitionBuilder, readElement);
    }

    /**
     * Visit a property write element.
     *
     * @param beanDefinitionBuilder The bean definition builder
     * @param propertyElement       The property
     * @param writeElement          The write element
     * @return true if processed
     */
    protected boolean visitPropertyWriteElement(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder,
                                                PropertyElement propertyElement,
                                                MemberElement writeElement) {
        if (writeElement instanceof MethodElement methodWriteElement) {
            return visitPropertyWriteElement(beanDefinitionBuilder, propertyElement, methodWriteElement);
        }
        return false;
    }

    /**
     * Visit a property write element.
     *
     * @param beanDefinitionBuilder The beanDefinitionBuilder
     * @param propertyElement       The property
     * @param writeElement          The write element
     * @return true if processed
     */
    @NextMajorVersion("Require @ReflectiveAccess for private methods in Micronaut 4")
    protected boolean visitPropertyWriteElement(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder,
                                                PropertyElement propertyElement,
                                                MethodElement writeElement) {
        makeInterceptedForValidationIfNeeded(writeElement);
        if (visitInjectAndLifecycleMethod(beanDefinitionBuilder, writeElement)) {
            makeInterceptedForValidationIfNeeded(writeElement);
            return true;
        } else if (!writeElement.isStatic() && writeElement.getMethodAnnotationMetadata().hasStereotype(AnnotationUtil.QUALIFIER)) {
            staticMethodCheck(writeElement);
            // TODO: Require @ReflectiveAccess for private methods in Micronaut 4
            visitMethodInjectionPoint(beanDefinitionBuilder, writeElement);
            return true;
        }
        return visitAopAndExecutableMethod(beanDefinitionBuilder, writeElement);
    }

    /**
     * Visit a method.
     *
     * @param beanDefinitionBuilder The bean definition builder
     * @param methodElement         The method
     * @return true if processed
     */
    protected boolean visitMethod(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, MethodElement methodElement) {
        if (visitInjectAndLifecycleMethod(beanDefinitionBuilder, methodElement)) {
            return true;
        }
        return visitAopAndExecutableMethod(beanDefinitionBuilder, methodElement);
    }

    @NextMajorVersion("Require @ReflectiveAccess for private methods in Micronaut 4")
    private boolean visitInjectAndLifecycleMethod(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, MethodElement methodElement) {
        // All the cases above are using executable methods
        boolean claimed = false;
        if (methodElement.hasDeclaredAnnotation(AnnotationUtil.POST_CONSTRUCT)) {
            staticMethodCheck(methodElement);
            // TODO: Require @ReflectiveAccess for private methods in Micronaut 4
            beanDefinitionBuilder.addPostConstruct(methodElement, methodElement.isReflectionRequired(classElement), visitorContext);
            claimed = true;
        }
        if (methodElement.hasDeclaredAnnotation(AnnotationUtil.PRE_DESTROY)) {
            staticMethodCheck(methodElement);
            // TODO: Require @ReflectiveAccess for private methods in Micronaut 4
            beanDefinitionBuilder.addPreDestroy(methodElement, methodElement.isReflectionRequired(classElement), visitorContext);
            claimed = true;
        }
        if (claimed) {
            return true;
        }
        if (!methodElement.isStatic() && isInjectPointMethod(methodElement)) {
            staticMethodCheck(methodElement);
            // TODO: Require @ReflectiveAccess for private methods in Micronaut 4
            visitMethodInjectionPoint(beanDefinitionBuilder, methodElement);
            return true;
        }
        return false;
    }

    /**
     * Visit a method injection point.
     *
     * @param beanDefinitionBuilder The bean definition builder
     * @param methodElement         The method element
     */
    protected void visitMethodInjectionPoint(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, MethodElement methodElement) {
        beanDefinitionBuilder.addMethodInjection(
            methodElement,
            methodElement.isReflectionRequired(classElement),
            visitorContext
        );
    }

    private boolean visitAopAndExecutableMethod(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, MethodElement methodElement) {
        if (methodElement.isStatic() && isExplicitlyAnnotatedAsExecutable(methodElement)) {
            // Only allow static executable methods when it's explicitly annotated with Executable.class
            return false;
        }
        if (methodElement.hasStereotype(Adapter.class)) {
            staticMethodCheck(methodElement);
            visitAdaptedMethod(methodElement);
            // Adapter is always an executable method but can also be intercepted so continue with visitors below
        }
        if (visitAopMethod(beanDefinitionBuilder, methodElement)) {
            return true;
        }
        return visitExecutableMethod(beanDefinitionBuilder, methodElement);
    }

    /**
     * Visit an AOP method.
     *
     * @param beanDefinitionBuilder The visitor
     * @param methodElement         The method
     * @return true if processed
     */
    protected boolean visitAopMethod(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, MethodElement methodElement) {
        boolean aopDefinedOnClassAndPublicMethod = isAopProxy && (methodElement.isPublic() || methodElement.isPackagePrivate());
        AnnotationMetadata methodAnnotationMetadata = methodElement.getMethodAnnotationMetadata();
        if (aopDefinedOnClassAndPublicMethod ||
            !isAopProxy && InterceptedMethodUtil.hasAroundStereotype(methodAnnotationMetadata) ||
            InterceptedMethodUtil.hasDeclaredAroundAdvice(methodAnnotationMetadata) && !classElement.isAbstract()) {
            if (methodElement.isFinal()) {
                if (InterceptedMethodUtil.hasDeclaredAroundAdvice(methodAnnotationMetadata)) {
                    throw new ProcessingException(methodElement, "Method defines AOP advice but is declared final. Change the method to be non-final in order for AOP advice to be applied.");
                } else if (!methodElement.isSynthetic() && aopDefinedOnClassAndPublicMethod && isDeclaredInThisClass(methodElement)) {
                    throw new ProcessingException(methodElement, "Public method inherits AOP advice but is declared final. Either make the method non-public or apply AOP advice only to public methods declared on the class.");
                }
                return false;
            } else if (methodElement.isPrivate()) {
                throw new ProcessingException(methodElement, "Method annotated as executable but is declared private. Change the method to be non-private in order for AOP advice to be applied.");
            } else if (methodElement.isStatic()) {
                throw new ProcessingException(methodElement, "Method defines AOP advice but is declared static");
            }
            ElementProxyBuilder<R> proxyBuilder = getAopProxyBuilder(beanDefinitionBuilder, methodElement);
            visitAroundMethod(proxyBuilder, classElement, methodElement);
            return true;
        }
        return false;
    }

    protected void visitAroundMethod(ElementProxyBuilder<R> proxyBuilder, TypedElement beanType, MethodElement methodElement) {
        proxyBuilder.addAroundMethod(methodElement);
    }

    /**
     * Is inject point method?
     *
     * @param memberElement The method
     * @return true if it is
     */
    protected boolean isInjectPointMethod(MemberElement memberElement) {
        return memberElement.hasDeclaredStereotype(AnnotationUtil.INJECT);
    }

    private void staticMethodCheck(MethodElement methodElement) {
        if (methodElement.isStatic()) {
            if (!isExplicitlyAnnotatedAsExecutable(methodElement)) {
                throw new ProcessingException(methodElement, "Static methods only allowed when annotated with @Executable");
            }
            failIfMethodNotAccessible(methodElement);
        }
    }

    private void failIfMethodNotAccessible(MethodElement methodElement) {
        if (!methodElement.isAccessible(classElement)) {
            throw new ProcessingException(methodElement, "Method is not accessible for the invocation. To invoke the method using reflection annotate it with @ReflectiveAccess");
        }
    }

    private static boolean isExplicitlyAnnotatedAsExecutable(MethodElement methodElement) {
        return !methodElement.getMethodAnnotationMetadata().hasDeclaredAnnotation(Executable.class);
    }

    /**
     * Visit a field.
     *
     * @param beanDefinitionBuilder The bean definition builder
     * @param fieldElement          The field
     * @return true if processed
     */
    protected boolean visitField(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, FieldElement fieldElement) {
        if (fieldElement.isStatic() || fieldElement.isFinal()) {
            return false;
        }
        AnnotationMetadata fieldAnnotationMetadata = fieldElement.getAnnotationMetadata();
        if (fieldAnnotationMetadata.hasStereotype(Value.class) || fieldAnnotationMetadata.hasStereotype(Property.class)) {
            beanDefinitionBuilder.addFieldPropertyInjection(fieldElement, fieldElement, fieldElement.isReflectionRequired(classElement), visitorContext);
            return true;
        }
        if (fieldAnnotationMetadata.hasStereotype(AnnotationUtil.INJECT)
            || fieldAnnotationMetadata.hasDeclaredStereotype(AnnotationUtil.QUALIFIER)) {
            beanDefinitionBuilder.addFieldInjection(fieldElement, fieldElement.isReflectionRequired(classElement), visitorContext);
            return true;
        }
        return false;
    }

    private void addOriginatingElementIfNecessary(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, MemberElement memberElement) {
        if (!memberElement.isSynthetic() && !isDeclaredInThisClass(memberElement)) {
            beanDefinitionBuilder.addOriginatingElement(memberElement.getDeclaringType());
        }
    }

    /**
     * Visit an executable method.
     *
     * @param beanDefinitionBuilder The bean definition builder
     * @param methodElement         The method
     * @return true if processed
     */
    protected boolean visitExecutableMethod(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, MethodElement methodElement) {
        if (!methodElement.hasStereotype(Executable.class)) {
            return false;
        }
        if (methodElement.isSynthetic()) {
            // Synthetic methods cannot be executable as @Executable cannot be put on a field
            return false;
        }
        if (methodElement.getMethodAnnotationMetadata().hasStereotype(Executable.class)) {
            // @Executable annotated on the method
            // Throw error if it cannot be accessed without the reflection
            if (!methodElement.isAccessible()) {
                throw new ProcessingException(methodElement, "Method annotated as executable but is declared private. To invoke the method using reflection annotate it with @ReflectiveAccess");
            }
        } else if (!isDeclaredInThisClass(methodElement) && !methodElement.getDeclaringType().hasStereotype(Executable.class)) {
            // @Executable not annotated on the declared class or method
            // Only include public methods
            if (!methodElement.isPublic()) {
                return false;
            }
        }
        // else
        // @Executable annotated on the class
        // only include own accessible methods or the ones annotated with @ReflectiveAccess
        if (methodElement.isAccessible()
            || !methodElement.isPrivate() && methodElement.getClass().getSimpleName().contains("Groovy")) {
            beanDefinitionBuilder.addExecutableMethod(methodElement, methodElement.isReflectionRequired(classElement));
        }
        return true;
    }

    private boolean isDeclaredInThisClass(MemberElement memberElement) {
        return classElement.equals(memberElement.getDeclaringType());
    }

    private void visitAdaptedMethod(MethodElement sourceMethod) {
        AnnotationMetadata methodAnnotationMetadata = sourceMethod.getDeclaredMetadata();

        Optional<ClassElement> interfaceToAdaptValue = methodAnnotationMetadata.getValue(Adapter.class, String.class)
            .flatMap(clazz -> visitorContext.getClassElement(clazz, visitorContext.getElementAnnotationMetadataFactory().readOnly()));

        if (interfaceToAdaptValue.isEmpty()) {
            return;
        }
        ClassElement interfaceToAdapt = interfaceToAdaptValue.get();
        if (!interfaceToAdapt.isInterface()) {
            throw new ProcessingException(sourceMethod, "Class to adapt [" + interfaceToAdapt.getName() + "] is not an interface");
        }

        List<MethodElement> methods = interfaceToAdapt.getEnclosedElements(ElementQuery.ALL_METHODS.onlyAbstract());
        if (methods.isEmpty()) {
            throw new ProcessingException(sourceMethod, "Interface to adapt [" + interfaceToAdapt.getName() + "] is not a SAM type. No methods found.");
        }
        if (methods.size() > 1) {
            throw new ProcessingException(sourceMethod, "Interface to adapt [" + interfaceToAdapt.getName() + "] is not a SAM type. More than one abstract method declared.");
        }

        MethodElement targetMethod = methods.getFirst();

        ParameterElement[] sourceParams = sourceMethod.getParameters();
        ParameterElement[] targetParams = targetMethod.getParameters();

        int paramLen = targetParams.length;
        if (paramLen != sourceParams.length) {
            throw new ProcessingException(sourceMethod, MSG_ADAPTER_METHOD_PREFIX + sourceMethod + MSG_TARGET_METHOD_PREFIX + targetMethod + "]. Argument lengths don't match.");
        }
        if (sourceMethod.isSuspend()) {
            throw new ProcessingException(sourceMethod, MSG_ADAPTER_METHOD_PREFIX + sourceMethod + MSG_TARGET_METHOD_PREFIX + targetMethod + "]. Kotlin suspend method not supported here.");
        }

        Map<String, ClassElement> typeVariables = interfaceToAdapt.getTypeArguments();
        Map<String, ClassElement> genericTypes = CollectionUtils.newLinkedHashMap(paramLen);
        for (int i = 0; i < paramLen; i++) {
            ParameterElement targetParam = targetParams[i];
            ParameterElement sourceParam = sourceParams[i];

            ClassElement targetType = targetParam.getType();
            ClassElement targetGenericType = targetParam.getGenericType();
            ClassElement sourceType = sourceParam.getGenericType();

            // ??? Java returns generic placeholder for the generic type and Groovy from the ordinary type
            if (targetGenericType instanceof GenericPlaceholderElement genericPlaceholderElement) {
                String variableName = genericPlaceholderElement.getVariableName();
                if (typeVariables.containsKey(variableName)) {
                    genericTypes.put(variableName, sourceType);
                }
            } else if (targetType instanceof GenericPlaceholderElement genericPlaceholderElement) {
                String variableName = genericPlaceholderElement.getVariableName();
                if (typeVariables.containsKey(variableName)) {
                    genericTypes.put(variableName, sourceType);
                }
            }

            if (!sourceType.isAssignable(targetGenericType.getName())) {
                throw new ProcessingException(sourceMethod, MSG_ADAPTER_METHOD_PREFIX + sourceMethod + MSG_TARGET_METHOD_PREFIX + targetMethod + "]. Type [" + sourceType.getName() + "] is not a subtype of type [" + targetGenericType.getName() + "] for argument at position " + i);
            }
        }

        String suffix = '$' + interfaceToAdapt.getSimpleName() + '$' + sourceMethod.getSimpleName() + adaptedMethodIndex.incrementAndGet();
        String adapterProxyClassName = classElement.getName() + suffix;

        interfaceToAdapt = interfaceToAdapt.withTypeArguments(genericTypes);

        AnnotationClassValue<?>[] adaptedArgumentTypes = Arrays.stream(sourceParams)
            .map(p -> new AnnotationClassValue<>(getClassName(p.getGenericType())))
            .toArray(AnnotationClassValue[]::new);

        interfaceToAdapt.annotate(Adapter.class, builder -> {
            builder.member(Adapter.InternalAttributes.ADAPTED_BEAN, new AnnotationClassValue<>(getClassName(classElement)));
            builder.member(Adapter.InternalAttributes.ADAPTED_METHOD, sourceMethod.getName());
            builder.member(Adapter.InternalAttributes.ADAPTED_ARGUMENT_TYPES, adaptedArgumentTypes);
            String qualifier = classElement.stringValue(AnnotationUtil.NAMED).orElse(null);
            if (StringUtils.isNotEmpty(qualifier)) {
                builder.member(Adapter.InternalAttributes.ADAPTED_QUALIFIER, qualifier);
            }
        });

        ClassElement finalInterfaceToAdapt1 = interfaceToAdapt;
        interfaceToAdapt.annotate(Indexed.class, builder -> builder.member(AnnotationMetadata.VALUE_MEMBER, new AnnotationClassValue<>(finalInterfaceToAdapt1.getName())));

        MutableAnnotationMetadata proxyAnnotationMetadata = MutableAnnotationMetadata.of(
            new AnnotationMetadataHierarchy(classElement, interfaceToAdapt)
        );

        // TODO: The best would be to add a requires for the adapted bean instead of copying all the annotations
        ElementProxyBuilder<R> aopProxyWriter = beanDefinitionBuilderFactory.introductionProxy(
            adapterProxyClassName,
            proxyAnnotationMetadata,
            interfaceToAdapt
        );
        additionalBuilders.add(aopProxyWriter);

        aopProxyWriter.implementInterface(interfaceToAdapt);
    }

    private static String getClassName(ClassElement element) {
        if (element.isArray()) {
            return getClassName(element.fromArray()) + "[]";
        }
        return element.getName();
    }

}
