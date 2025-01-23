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
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.internal.intercepted.InterceptedMethodUtil;
import io.micronaut.aop.writer.AopProxyWriter;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NextMajorVersion;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionVisitor;
import io.micronaut.inject.writer.BeanDefinitionWriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ordinary declared bean.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
class DeclaredBeanElementCreator extends AbstractBeanElementCreator {

    private static final String MSG_ADAPTER_METHOD_PREFIX = "Cannot adapt method [";
    private static final String MSG_TARGET_METHOD_PREFIX = "] to target method [";

    protected AopProxyWriter aopProxyVisitor;
    protected final boolean isAopProxy;
    private final AtomicInteger adaptedMethodIndex = new AtomicInteger(0);

    protected DeclaredBeanElementCreator(ClassElement classElement, VisitorContext visitorContext, boolean isAopProxy) {
        super(classElement, visitorContext);
        this.isAopProxy = isAopProxy;
    }

    @Override
    public final void buildInternal() {
        BeanDefinitionVisitor beanDefinitionVisitor = createBeanDefinitionVisitor();
        if (isAopProxy) {
            // Always create AOP proxy
            getAroundAopProxyVisitor(beanDefinitionVisitor, null);
        }
        build(beanDefinitionVisitor);
    }

    /**
     * Create a bean definition visitor.
     *
     * @return the visitor
     */
    @NonNull
    protected BeanDefinitionVisitor createBeanDefinitionVisitor() {
        BeanDefinitionVisitor beanDefinitionWriter = new BeanDefinitionWriter(classElement, visitorContext);
        beanDefinitionWriters.add(beanDefinitionWriter);
        beanDefinitionWriter.visitTypeArguments(classElement.getAllTypeArguments());
        visitAnnotationMetadata(beanDefinitionWriter, classElement.getAnnotationMetadata());
        MethodElement constructorElement = classElement.getPrimaryConstructor().orElse(null);
        if (constructorElement != null) {
            applyConfigurationInjectionIfNecessary(beanDefinitionWriter, constructorElement);
            beanDefinitionWriter.visitBeanDefinitionConstructor(constructorElement, constructorElement.isPrivate(), visitorContext);
        } else {
            beanDefinitionWriter.visitDefaultConstructor(AnnotationMetadata.EMPTY_METADATA, visitorContext);
        }
        return beanDefinitionWriter;
    }

    /**
     * Create an AOP proxy visitor.
     *
     * @param visitor       the parent visitor
     * @param methodElement the method that is originating the AOP proxy
     * @return The AOP proxy visitor
     */
    protected AopProxyWriter getAroundAopProxyVisitor(BeanDefinitionVisitor visitor, @Nullable MethodElement methodElement) {
        if (aopProxyVisitor == null) {
            if (classElement.isFinal()) {
                throw new ProcessingException(classElement, "Cannot apply AOP advice to final class. Class must be made non-final to support proxying: " + classElement.getName());
            }
            aopProxyVisitor = createAroundAopProxyWriter(
                visitor,
                isAopProxy || methodElement == null ? classElement.getAnnotationMetadata() : methodElement.getAnnotationMetadata(),
                visitorContext,
                false
            );
            beanDefinitionWriters.add(aopProxyVisitor);
            MethodElement constructorElement = classElement.getPrimaryConstructor().orElse(null);
            if (constructorElement != null) {
                aopProxyVisitor.visitBeanDefinitionConstructor(
                    constructorElement,
                    constructorElement.isPrivate(),
                    visitorContext
                );
            } else {
                aopProxyVisitor.visitDefaultConstructor(
                    AnnotationMetadata.EMPTY_METADATA,
                    visitorContext
                );
            }
            aopProxyVisitor.visitSuperBeanDefinition(visitor.getBeanDefinitionName());
        }
        return aopProxyVisitor;
    }

    /**
     * @return true if the class should be processed as a properties bean
     */
    protected boolean processAsProperties() {
        return false;
    }

    private void build(BeanDefinitionVisitor visitor) {
        Set<FieldElement> processedFields = new HashSet<>();
        ElementQuery<MemberElement> memberQuery = ElementQuery.ALL_FIELD_AND_METHODS.includeHiddenElements();
        if (processAsProperties()) {
            memberQuery = memberQuery.excludePropertyElements();
            for (PropertyElement propertyElement : classElement.getBeanProperties()) {
                if (visitPropertyInternal(visitor, propertyElement)) {
                    propertyElement.getField().ifPresent(processedFields::add);
                }
            }
        } else {
            for (PropertyElement propertyElement : classElement.getSyntheticBeanProperties()) {
                if (visitPropertyInternal(visitor, propertyElement)) {
                    propertyElement.getField().ifPresent(processedFields::add);
                }
            }
        }
        List<MemberElement> memberElements = new ArrayList<>(classElement.getEnclosedElements(memberQuery));
        memberElements.removeAll(processedFields);
        for (MemberElement memberElement : memberElements) {
            if (memberElement instanceof FieldElement fieldElement) {
                visitFieldInternal(visitor, fieldElement);
            } else if (memberElement instanceof MethodElement methodElement) {
                visitMethodInternal(visitor, methodElement);
            } else if (!(memberElement instanceof PropertyElement)) {
                throw new IllegalStateException("Unknown element");
            }
        }
    }

    private void visitFieldInternal(BeanDefinitionVisitor visitor, FieldElement fieldElement) {
        boolean claimed = visitField(visitor, fieldElement);
        if (claimed) {
            addOriginatingElementIfNecessary(visitor, fieldElement);
        }
    }

    private void visitMethodInternal(BeanDefinitionVisitor visitor, MethodElement methodElement) {
        makeInterceptedForValidationIfNeeded(methodElement);
        boolean claimed = visitMethod(visitor, methodElement);
        if (claimed) {
            addOriginatingElementIfNecessary(visitor, methodElement);
        }
    }

    private boolean visitPropertyInternal(BeanDefinitionVisitor visitor, PropertyElement propertyElement) {
        boolean claimed = visitProperty(visitor, propertyElement);
        if (claimed) {
            propertyElement.getReadMethod().ifPresent(element -> addOriginatingElementIfNecessary(visitor, element));
            propertyElement.getWriteMethod().ifPresent(element -> addOriginatingElementIfNecessary(visitor, element));
            propertyElement.getField().ifPresent(element -> addOriginatingElementIfNecessary(visitor, element));
        }
        return claimed;
    }

    /**
     * Visit a property.
     *
     * @param visitor         The visitor
     * @param propertyElement The property
     * @return true if processed
     */
    protected boolean visitProperty(BeanDefinitionVisitor visitor, PropertyElement propertyElement) {
        boolean claimed = false;
        Optional<? extends MemberElement> writeMember = propertyElement.getWriteMember();
        if (writeMember.isPresent()) {
            claimed |= visitPropertyWriteElement(visitor, propertyElement, writeMember.get());
        }
        Optional<? extends MemberElement> readMember = propertyElement.getReadMember();
        if (readMember.isPresent()) {
            boolean readElementClaimed = visitPropertyReadElement(visitor, propertyElement, readMember.get());
            claimed |= readElementClaimed;
        }
        // Process property's field if no methods were processed
        Optional<FieldElement> field = propertyElement.getField();
        if (!claimed && field.isPresent()) {
            FieldElement writeElement = field.get();
            claimed = visitField(visitor, writeElement);
        }
        return claimed;
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
     * Visit a property read element.
     *
     * @param visitor         The visitor
     * @param propertyElement The property
     * @param readElement     The read element
     * @return true if processed
     */
    protected boolean visitPropertyReadElement(BeanDefinitionVisitor visitor,
                                               PropertyElement propertyElement,
                                               MemberElement readElement) {
        if (readElement instanceof MethodElement methodReadElement) {
            return visitPropertyReadElement(visitor, propertyElement, methodReadElement);
        }
        return false;
    }

    /**
     * Visit a property method read element.
     *
     * @param visitor         The visitor
     * @param propertyElement The property
     * @param readElement     The read element
     * @return true if processed
     */
    protected boolean visitPropertyReadElement(BeanDefinitionVisitor visitor,
                                               PropertyElement propertyElement,
                                               MethodElement readElement) {
        return visitAopAndExecutableMethod(visitor, readElement);
    }

    /**
     * Visit a property write element.
     *
     * @param visitor         The visitor
     * @param propertyElement The property
     * @param writeElement    The write element
     * @return true if processed
     */
    protected boolean visitPropertyWriteElement(BeanDefinitionVisitor visitor,
                                                PropertyElement propertyElement,
                                                MemberElement writeElement) {
        if (writeElement instanceof MethodElement methodWriteElement) {
            return visitPropertyWriteElement(visitor, propertyElement, methodWriteElement);
        }
        return false;
    }

    /**
     * Visit a property write element.
     *
     * @param visitor         The visitor
     * @param propertyElement The property
     * @param writeElement    The write element
     * @return true if processed
     */
    @NextMajorVersion("Require @ReflectiveAccess for private methods in Micronaut 4")
    protected boolean visitPropertyWriteElement(BeanDefinitionVisitor visitor,
                                                PropertyElement propertyElement,
                                                MethodElement writeElement) {
        makeInterceptedForValidationIfNeeded(writeElement);
        if (visitInjectAndLifecycleMethod(visitor, writeElement)) {
            makeInterceptedForValidationIfNeeded(writeElement);
            return true;
        } else if (!writeElement.isStatic() && writeElement.getMethodAnnotationMetadata().hasStereotype(AnnotationUtil.QUALIFIER)) {
            if (propertyElement.getReadMethod().isPresent() && writeElement.hasStereotype(ANN_REQUIRES_VALIDATION)) {
                visitor.setValidated(true);
            }
            staticMethodCheck(writeElement);
            // TODO: Require @ReflectiveAccess for private methods in Micronaut 4
            visitMethodInjectionPoint(visitor, writeElement);
            return true;
        }
        return visitAopAndExecutableMethod(visitor, writeElement);
    }

    /**
     * Visit a method.
     *
     * @param visitor       The visitor
     * @param methodElement The method
     * @return true if processed
     */
    protected boolean visitMethod(BeanDefinitionVisitor visitor, MethodElement methodElement) {
        if (visitInjectAndLifecycleMethod(visitor, methodElement)) {
            return true;
        }
        return visitAopAndExecutableMethod(visitor, methodElement);
    }

    @NextMajorVersion("Require @ReflectiveAccess for private methods in Micronaut 4")
    private boolean visitInjectAndLifecycleMethod(BeanDefinitionVisitor visitor, MethodElement methodElement) {
        // All the cases above are using executable methods
        boolean claimed = false;
        if (methodElement.hasDeclaredAnnotation(AnnotationUtil.POST_CONSTRUCT)) {
            staticMethodCheck(methodElement);
            // TODO: Require @ReflectiveAccess for private methods in Micronaut 4
            visitor.visitPostConstructMethod(
                methodElement.getDeclaringType(),
                methodElement,
                methodElement.isReflectionRequired(classElement),
                visitorContext);
            claimed = true;
        }
        if (methodElement.hasDeclaredAnnotation(AnnotationUtil.PRE_DESTROY)) {
            staticMethodCheck(methodElement);
            // TODO: Require @ReflectiveAccess for private methods in Micronaut 4
            visitor.visitPreDestroyMethod(
                methodElement.getDeclaringType(),
                methodElement,
                methodElement.isReflectionRequired(classElement),
                visitorContext
            );
            claimed = true;
        }
        if (claimed) {
            return true;
        }
        if (!methodElement.isStatic() && isInjectPointMethod(methodElement)) {
            staticMethodCheck(methodElement);
            // TODO: Require @ReflectiveAccess for private methods in Micronaut 4
            visitMethodInjectionPoint(visitor, methodElement);
            return true;
        }
        return false;
    }

    /**
     * Visit a method injection point.
     * @param visitor The visitor
     * @param methodElement The method element
     */
    protected void visitMethodInjectionPoint(BeanDefinitionVisitor visitor, MethodElement methodElement) {
        applyConfigurationInjectionIfNecessary(visitor, methodElement);
        visitor.visitMethodInjectionPoint(
            methodElement.getDeclaringType(),
            methodElement,
            methodElement.isReflectionRequired(classElement),
            visitorContext
        );
    }

    private boolean visitAopAndExecutableMethod(BeanDefinitionVisitor visitor, MethodElement methodElement) {
        if (methodElement.isStatic() && isExplicitlyAnnotatedAsExecutable(methodElement)) {
            // Only allow static executable methods when it's explicitly annotated with Executable.class
            return false;
        }
        // This method requires pre-processing. See Executable#processOnStartup()
        boolean preprocess = methodElement.isTrue(Executable.class, "processOnStartup");
        if (preprocess) {
            visitor.setRequiresMethodProcessing(true);
        }
        if (methodElement.hasStereotype(Adapter.class)) {
            staticMethodCheck(methodElement);
            visitAdaptedMethod(methodElement);
            // Adapter is always an executable method but can also be intercepted so continue with visitors below
        }
        if (visitAopMethod(visitor, methodElement)) {
            return true;
        }
        return visitExecutableMethod(visitor, methodElement);
    }

    /**
     * Visit an AOP method.
     *
     * @param visitor       The visitor
     * @param methodElement The method
     * @return true if processed
     */
    protected boolean visitAopMethod(BeanDefinitionVisitor visitor, MethodElement methodElement) {
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
            AopProxyWriter aopProxyVisitor = getAroundAopProxyVisitor(visitor, methodElement);
            visitAroundMethod(aopProxyVisitor, classElement, methodElement);
            return true;
        }
        return false;
    }

    protected void visitAroundMethod(AopProxyWriter aopProxyWriter, TypedElement beanType, MethodElement methodElement) {
        aopProxyWriter.visitInterceptorBinding(
            InterceptedMethodUtil.resolveInterceptorBinding(methodElement.getAnnotationMetadata(), InterceptorKind.AROUND)
        );
        aopProxyWriter.visitAroundMethod(beanType, methodElement);
    }

    /**
     * Apply configuration injection for the constructor.
     *
     * @param visitor     The visitor
     * @param constructor The constructor
     */
    protected void applyConfigurationInjectionIfNecessary(BeanDefinitionVisitor visitor,
                                                          MethodElement constructor) {
        // default to do nothing
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
     * @param visitor      The visitor
     * @param fieldElement The field
     * @return true if processed
     */
    protected boolean visitField(BeanDefinitionVisitor visitor, FieldElement fieldElement) {
        if (fieldElement.isStatic() || fieldElement.isFinal()) {
            return false;
        }
        AnnotationMetadata fieldAnnotationMetadata = fieldElement.getAnnotationMetadata();
        boolean isRequired = InjectionPoint.isInjectionRequired(fieldElement);
        if (fieldAnnotationMetadata.hasStereotype(Value.class) || fieldAnnotationMetadata.hasStereotype(Property.class)) {
            visitor.visitFieldValue(
                fieldElement.getDeclaringType(),
                fieldElement,
                fieldElement.isReflectionRequired(classElement),
                !isRequired
            );
            return true;
        }
        if (fieldAnnotationMetadata.hasStereotype(AnnotationUtil.INJECT)
            || fieldAnnotationMetadata.hasDeclaredStereotype(AnnotationUtil.QUALIFIER)) {
            visitor.visitFieldInjectionPoint(
                fieldElement.getDeclaringType(),
                fieldElement,
                fieldElement.isReflectionRequired(classElement),
                visitorContext
            );
            return true;
        }
        return false;
    }

    private void addOriginatingElementIfNecessary(BeanDefinitionVisitor writer, MemberElement memberElement) {
        if (!memberElement.isSynthetic() && !isDeclaredInThisClass(memberElement)) {
            writer.addOriginatingElement(memberElement.getDeclaringType());
        }
    }

    /**
     * Visit an executable method.
     *
     * @param visitor       The visitor
     * @param methodElement The method
     * @return true if processed
     */
    protected boolean visitExecutableMethod(BeanDefinitionVisitor visitor, MethodElement methodElement) {
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
            visitor.visitExecutableMethod(classElement, methodElement, visitorContext);
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

        if (!genericTypes.isEmpty()) {
            aopProxyWriter.visitTypeArguments(Collections.singletonMap(interfaceToAdapt.getName(), genericTypes));
        }

        AnnotationClassValue<?>[] adaptedArgumentTypes = Arrays.stream(sourceParams)
            .map(p -> new AnnotationClassValue<>(getClassName(p.getGenericType())))
            .toArray(AnnotationClassValue[]::new);

        targetMethod = targetMethod.withNewOwningType(classElement);

        targetMethod.annotate(Adapter.class, builder -> {
            builder.member(Adapter.InternalAttributes.ADAPTED_BEAN, new AnnotationClassValue<>(getClassName(classElement)));
            builder.member(Adapter.InternalAttributes.ADAPTED_METHOD, sourceMethod.getName());
            builder.member(Adapter.InternalAttributes.ADAPTED_ARGUMENT_TYPES, adaptedArgumentTypes);
            String qualifier = classElement.stringValue(AnnotationUtil.NAMED).orElse(null);
            if (StringUtils.isNotEmpty(qualifier)) {
                builder.member(Adapter.InternalAttributes.ADAPTED_QUALIFIER, qualifier);
            }
        });

        aopProxyWriter.visitAroundMethod(interfaceToAdapt, targetMethod);

        beanDefinitionWriters.add(aopProxyWriter);
    }

    private static String getClassName(ClassElement element) {
        if (element.isArray()) {
            return getClassName(element.fromArray()) + "[]";
        }
        return element.getName();
    }

}
