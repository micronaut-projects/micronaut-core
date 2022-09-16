package io.micronaut.inject.processing.gen;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionVisitor;
import io.micronaut.inject.writer.BeanDefinitionWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleBeanBuilder extends AbstractBeanBuilder {

    private final AtomicInteger adaptedMethodIndex = new AtomicInteger(0);

    protected SimpleBeanBuilder(ClassElement classElement, VisitorContext visitorContext) {
        super(classElement, visitorContext);
    }

    @Override
    public final void build() {
        BeanDefinitionVisitor beanDefinitionWriter = new BeanDefinitionWriter(classElement, metadataBuilder, visitorContext);
        beanDefinitionWriters.add(beanDefinitionWriter);
        beanDefinitionWriter.visitTypeArguments(classElement.getAllTypeArguments());
        visitAnnotationMetadata(beanDefinitionWriter, classElement.getAnnotationMetadata());
        MethodElement constructorElement = findConstructorElement(classElement).orElse(null);
        if (constructorElement != null) {
            beanDefinitionWriter.visitBeanDefinitionConstructor(constructorElement, constructorElement.isPrivate(), visitorContext);
        } else {
            beanDefinitionWriter.visitDefaultConstructor(AnnotationMetadata.EMPTY_METADATA, visitorContext);
        }
        build(beanDefinitionWriter);
    }

    protected void build(BeanDefinitionVisitor visitor) {
        List<FieldElement> fields = classElement.getEnclosedElements(ElementQuery.ALL_FIELDS.includeHiddenElements());
        List<FieldElement> declaredFields = new ArrayList<>(fields.size());
        // Process subtype fields first
        for (FieldElement fieldElement : fields) {
            if (fieldElement.getDeclaringType().equals(classElement)) {
                declaredFields.add(fieldElement);
            } else {
                visitFieldInternal(visitor, fieldElement);
            }
        }
        List<MethodElement> methods = classElement.getEnclosedElements(ElementQuery.ALL_METHODS);
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
        // TODO: eliminate the need to do the adjustment in the future
        adjustMethodToIncludeClassMetadata(visitor, methodElement);
        if (methodElement.hasAnnotation(ANN_REQUIRES_VALIDATION)) {
            methodElement.annotate(ANN_VALIDATED);
        }
        boolean claimed = visitMethod(visitor, methodElement);
        if (claimed) {
            addOriginatingElementIfNecessary(visitor, methodElement);
        }
    }

    protected boolean visitMethod(BeanDefinitionVisitor visitor, MethodElement methodElement) {
        // All the cases above are using executable methods
        if (methodElement.hasDeclaredAnnotation(AnnotationUtil.POST_CONSTRUCT)) {
            staticMethodCheck(methodElement);
            // TODO: Require @ReflectiveAccess for private methods in Micronaut 4
            visitor.visitPostConstructMethod(
                methodElement.getDeclaringType(),
                methodElement,
                methodElement.isReflectionRequired(classElement),
                visitorContext
            );
            return true;
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
            return true;
        }
        if (!methodElement.isStatic() && isInjectPointMethod(methodElement)) {
            staticMethodCheck(methodElement);
            // TODO: Require @ReflectiveAccess for private methods in Micronaut 4
            visitor.visitMethodInjectionPoint(
                methodElement.getDeclaringType(),
                methodElement,
                methodElement.isReflectionRequired(classElement),
                visitorContext
            );
            return true;
        }
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
        if (beforeExecutableMethod(visitor, methodElement)) {
            return true;
        }
        if (methodElement.hasStereotype(Executable.class)) {
            return visitExecutableMethod(visitor, methodElement);
        }
        return false;
    }

    protected boolean beforeExecutableMethod(BeanDefinitionVisitor visitor, MethodElement methodElement) {
        return false;
    }

    protected boolean isInjectPointMethod(MethodElement methodElement) {
        return methodElement.hasDeclaredStereotype(AnnotationUtil.INJECT);
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

    protected boolean visitField(BeanDefinitionVisitor visitor, FieldElement fieldElement) {
        if (fieldElement.isStatic() || fieldElement.isFinal()) {
            return false;
        }
        AnnotationMetadata fieldAnnotationMetadata = fieldElement.getAnnotationMetadata();
        if (fieldAnnotationMetadata.hasStereotype(Value.class) || fieldAnnotationMetadata.hasStereotype(Property.class)) {
            visitor.visitFieldValue(fieldElement.getDeclaringType(), fieldElement, fieldElement.isReflectionRequired(classElement), isOptionalFieldValue());
            return true;
        }
        if (fieldAnnotationMetadata.hasStereotype(AnnotationUtil.INJECT)
            || (fieldAnnotationMetadata.hasDeclaredStereotype(AnnotationUtil.QUALIFIER) && !fieldAnnotationMetadata.hasDeclaredAnnotation(Bean.class))) {
            visitor.visitFieldInjectionPoint(fieldElement.getDeclaringType(), fieldElement, fieldElement.isReflectionRequired(classElement));
            return true;
        }
        return false;
    }

    protected boolean isOptionalFieldValue() {
        return false;
    }

    protected void addOriginatingElementIfNecessary(BeanDefinitionVisitor writer, MemberElement memberElement) {
        if (!isDeclaredInThisClass(memberElement)) {
            writer.addOriginatingElement(memberElement);
        }
    }

//    public BeanDefinitionVisitor getOrCreateBeanDefinitionWriter(TypeElement classElement, Name qualifiedName) {
//        BeanDefinitionVisitor beanDefinitionWriter = beanDefinitionWriters.get(qualifiedName);
//        if (beanDefinitionWriter == null) {
//
//            beanDefinitionWriter = createBeanDefinitionWriterFor(classElement);
//            Name proxyKey = createProxyKey(beanDefinitionWriter.getBeanDefinitionName());
//            beanDefinitionWriters.put(qualifiedName, beanDefinitionWriter);
//
//
//            BeanDefinitionVisitor proxyWriter = beanDefinitionWriters.get(proxyKey);
//            final AnnotationMetadata annotationMetadata = new AnnotationMetadataHierarchy(
//                concreteClassMetadata,
//                constructorAnnotationMetadata
//            );
//            if (proxyWriter != null) {
//                if (constructorElement != null) {
//                    proxyWriter.visitBeanDefinitionConstructor(
//                        constructorElement,
//                        constructorElement.isPrivate(),
//                        javaVisitorContext
//                    );
//                } else {
//                    proxyWriter.visitDefaultConstructor(
//                        AnnotationMetadata.EMPTY_METADATA,
//                        javaVisitorContext
//                    );
//                }
//            }
//
//            if (constructorElement != null) {
//                beanDefinitionWriter.visitBeanDefinitionConstructor(
//                    constructorElement,
//                    constructorElement.isPrivate(),
//                    javaVisitorContext
//                );
//            } else {
//                beanDefinitionWriter.visitDefaultConstructor(annotationMetadata, javaVisitorContext);
//            }
//        }
//        return beanDefinitionWriter;
//    }

    protected boolean visitExecutableMethod(BeanDefinitionVisitor visitor, MethodElement methodElement) {
        if (methodElement.getAnnotationMetadata().getDeclaredMetadata().hasStereotype(Executable.class)) {
            // @Executable annotated on the method
            // Throw error if it cannot be accessed without the reflection
            if (!methodElement.isAccessible()) {
                throw new ProcessingException(methodElement, "Method annotated as executable but is declared private. To invoke the method using reflection annotate it with @ReflectiveAccess");
            }
        }
        if (!isDeclaredInThisClass(methodElement)) {
            // @Executable annotated on the parent class
            // Only include public methods
            if (!methodElement.isPublic()) {
                return false;
            }
        }
        // else
        // @Executable annotated on the class
        // only include own accessible methods or the ones annotated with @ReflectiveAccess
        if (methodElement.isAccessible()) {
            visitor.visitExecutableMethod(classElement, methodElement, visitorContext);
        }
        return true;
    }

    protected boolean isDeclaredInThisClass(MemberElement memberElement) {
        return classElement.getName().equals(memberElement.getDeclaringType().getName());
    }

    private void visitAdaptedMethod(BeanDefinitionVisitor visitor, MethodElement sourceMethod) {
        BeanDefinitionVisitor adapter = aopHelper
            .visitAdaptedMethod(classElement, sourceMethod, metadataBuilder, adaptedMethodIndex, visitorContext);
        if (adapter != null) {
            visitor.visitExecutableMethod(sourceMethod.getDeclaringType(), sourceMethod, visitorContext);
            beanDefinitionWriters.add(adapter);
        }
    }

}
