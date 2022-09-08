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

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.NextMajorVersion;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ProcessingException;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionVisitor;
import io.micronaut.inject.writer.BeanDefinitionWriter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ordinary declared bean.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public class DeclaredBeanDefinitionBuilder extends AbstractBeanDefinitionBuilder {

    protected BeanDefinitionVisitor aopProxyVisitor;
    protected final boolean isAopProxy;
    private final AtomicInteger adaptedMethodIndex = new AtomicInteger(0);

    protected DeclaredBeanDefinitionBuilder(ClassElement classElement, VisitorContext visitorContext, boolean isAopProxy) {
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
    protected BeanDefinitionVisitor getAroundAopProxyVisitor(BeanDefinitionVisitor visitor, @Nullable MethodElement methodElement) {
        if (aopProxyVisitor == null) {
            if (classElement.isFinal()) {
                throw new ProcessingException(classElement, "Cannot apply AOP advice to final class. Class must be made non-final to support proxying: " + classElement.getName());
            }
            aopProxyVisitor = aopHelper.createAroundAopProxyWriter(
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
        if (!processAsProperties()) {
            for (PropertyElement propertyElement : classElement.getNativeBeanProperties()) {
                propertyElement.getField().ifPresent(processedFields::add);
                visitPropertyInternal(visitor, propertyElement);
            }
        }
        ElementQuery<FieldElement> fieldsQuery = ElementQuery.ALL_FIELDS.includeHiddenElements();
        ElementQuery<MethodElement> membersQuery = ElementQuery.ALL_METHODS;
        boolean processAsProperties = processAsProperties();
        if (processAsProperties) {
            fieldsQuery = fieldsQuery.excludePropertyElements();
            membersQuery = membersQuery.excludePropertyElements();
            for (PropertyElement propertyElement : classElement.getBeanProperties()) {
                visitPropertyInternal(visitor, propertyElement);
            }
        }
        List<FieldElement> fields = new ArrayList<>(classElement.getEnclosedElements(fieldsQuery));
        fields.removeIf(processedFields::contains);
        List<FieldElement> declaredFields = new ArrayList<>(fields.size());
        // Process subtype fields first
        for (FieldElement fieldElement : fields) {
            if (fieldElement.getDeclaringType().equals(classElement)) {
                declaredFields.add(fieldElement);
            } else {
                visitFieldInternal(visitor, fieldElement);
            }
        }
        List<MethodElement> methods = classElement.getEnclosedElements(membersQuery);
        List<MethodElement> declaredMethods = new ArrayList<>(methods.size());
        // Process subtype methods first
        for (MethodElement methodElement : methods) {
            if (methodElement.getDeclaringType().equals(classElement)) {
                declaredMethods.add(methodElement);
            } else {
                visitMethodInternal(visitor, methodElement);
            }
        }
        for (FieldElement fieldElement : declaredFields) {
            visitFieldInternal(visitor, fieldElement);
        }
        for (MethodElement methodElement : declaredMethods) {
            visitMethodInternal(visitor, methodElement);
        }
    }

    private void visitFieldInternal(BeanDefinitionVisitor visitor, FieldElement fieldElement) {
        boolean claimed = visitField(visitor, fieldElement);
        if (claimed) {
            addOriginatingElementIfNecessary(visitor, fieldElement);
        }
    }

    private void visitMethodInternal(BeanDefinitionVisitor visitor, MethodElement methodElement) {
        if (methodElement.hasAnnotation(ANN_REQUIRES_VALIDATION)) {
            methodElement.annotate(ANN_VALIDATED);
        }
        boolean claimed = visitMethod(visitor, methodElement);
        if (claimed) {
            addOriginatingElementIfNecessary(visitor, methodElement);
        }
    }

    private void visitPropertyInternal(BeanDefinitionVisitor visitor, PropertyElement propertyElement) {
        if (propertyElement.hasAnnotation(ANN_REQUIRES_VALIDATION)) {
            propertyElement.annotate(ANN_VALIDATED);
        }
        boolean claimed = visitProperty(visitor, propertyElement);
        if (claimed) {
            propertyElement.getReadMethod().ifPresent(element -> addOriginatingElementIfNecessary(visitor, element));
            propertyElement.getWriteMethod().ifPresent(element -> addOriginatingElementIfNecessary(visitor, element));
            propertyElement.getField().ifPresent(element -> addOriginatingElementIfNecessary(visitor, element));
        }
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
        Optional<MethodElement> writeMethod = propertyElement.getWriteMethod();
        if (writeMethod.isPresent()) {
            MethodElement writeElement = writeMethod.get();
            claimed |= visitPropertyWriteElement(visitor, propertyElement, writeElement);
        }
        Optional<MethodElement> readMethod = propertyElement.getReadMethod();
        if (readMethod.isPresent()) {
            MethodElement readElement = readMethod.get();
            claimed |= visitPropertyReadElement(visitor, propertyElement, readElement);
        }
        // Process property's field if no methods were processed
        if (!claimed && propertyElement.getField().isPresent()) {
            FieldElement writeElement = propertyElement.getField().get();
            claimed = visitField(visitor, writeElement);
        }
        return claimed;
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
    @NextMajorVersion("Require @ReflectiveAccess for private methods in Micronaut 4")
    protected boolean visitPropertyWriteElement(BeanDefinitionVisitor visitor,
                                                PropertyElement propertyElement,
                                                MethodElement writeElement) {
        if (visitInjectAndLifecycleMethod(visitor, writeElement)) {
            return true;
        } else if (!writeElement.isStatic() && getElementAnnotationMetadata(writeElement).hasStereotype(AnnotationUtil.QUALIFIER)) {
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

    private void visitMethodInjectionPoint(BeanDefinitionVisitor visitor, MethodElement methodElement) {
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
        if (methodElement.hasStereotype("io.micronaut.aop.Adapter")) {
            staticMethodCheck(methodElement);
            visitAdaptedMethod(visitor, methodElement);
            return true;
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
        AnnotationMetadata methodAnnotationMetadata = getElementAnnotationMetadata(methodElement);
        if (aopDefinedOnClassAndPublicMethod ||
            !isAopProxy && hasAroundStereotype(methodAnnotationMetadata) ||
            hasDeclaredAroundAdvice(methodAnnotationMetadata) && !classElement.isAbstract()) {
            if (methodElement.isFinal()) {
                if (hasDeclaredAroundAdvice(methodAnnotationMetadata)) {
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
            BeanDefinitionVisitor aopProxyVisitor = getAroundAopProxyVisitor(visitor, methodElement);
            aopHelper.visitAroundMethod(aopProxyVisitor, classElement, methodElement);
            return true;
        }
        return false;
    }

    /**
     * Apply configuration injection for the constructor.
     *
     * @param visitor     The visitor
     * @param constructor The constructor
     */
    protected void applyConfigurationInjectionIfNecessary(BeanDefinitionVisitor visitor,
                                                          MethodElement constructor) {
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
        return !getElementAnnotationMetadata(methodElement).hasDeclaredAnnotation(Executable.class);
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
        if (fieldAnnotationMetadata.hasStereotype(Value.class) || fieldAnnotationMetadata.hasStereotype(Property.class)) {
            visitor.visitFieldValue(fieldElement.getDeclaringType(), fieldElement, false, fieldElement.isReflectionRequired(classElement));
            return true;
        }
        if (fieldAnnotationMetadata.hasStereotype(AnnotationUtil.INJECT)
            || fieldAnnotationMetadata.hasDeclaredStereotype(AnnotationUtil.QUALIFIER)) {
            visitor.visitFieldInjectionPoint(fieldElement.getDeclaringType(), fieldElement, fieldElement.isReflectionRequired(classElement));
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
        if (getElementAnnotationMetadata(methodElement).hasStereotype(Executable.class)) {
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

    private void visitAdaptedMethod(BeanDefinitionVisitor visitor, MethodElement sourceMethod) {
        BeanDefinitionVisitor adapter = aopHelper
            .visitAdaptedMethod(classElement, sourceMethod, adaptedMethodIndex, visitorContext);
        if (adapter != null) {
            visitor.visitExecutableMethod(sourceMethod.getDeclaringType(), sourceMethod, visitorContext);
            beanDefinitionWriters.add(adapter);
        }
    }

}
