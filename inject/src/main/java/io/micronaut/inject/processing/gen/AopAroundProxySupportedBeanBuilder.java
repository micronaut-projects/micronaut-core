package io.micronaut.inject.processing.gen;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionVisitor;

public class AopAroundProxySupportedBeanBuilder extends SimpleBeanBuilder {

    private final boolean isAopProxy;
    protected BeanDefinitionVisitor aopProxyVisitor;

    protected AopAroundProxySupportedBeanBuilder(ClassElement classElement, VisitorContext visitorContext, boolean isAopProxy) {
        super(classElement, visitorContext);
        this.isAopProxy = isAopProxy;
    }

    protected BeanDefinitionVisitor getAroundAopProxyVisitor(BeanDefinitionVisitor visitor, @Nullable MethodElement methodElement) {
        if (aopProxyVisitor == null) {
            if (classElement.isFinal()) {
                throw new ProcessingException(classElement, "Cannot apply AOP advice to final class. Class must be made non-final to support proxying: " + classElement.getName());
            }

            aopProxyVisitor = aopHelper.createAroundAopProxyWriter(
                visitor,
                isAopProxy || methodElement == null ? classElement.getAnnotationMetadata() : methodElement.getAnnotationMetadata(),
                metadataBuilder,
                visitorContext,
                false
            );
            beanDefinitionWriters.add(aopProxyVisitor);
            MethodElement constructorElement = findConstructorElement(classElement).orElse(null);
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

    @Override
    protected boolean beforeExecutableMethod(BeanDefinitionVisitor visitor, MethodElement methodElement) {
        boolean aopDefinedOnClassAndPublicMethod = isAopProxy && (methodElement.isPublic() || methodElement.isPackagePrivate());
        AnnotationMetadata methodAnnotationMetadata = getElementAnnotationMetadata(methodElement);
        if (aopDefinedOnClassAndPublicMethod ||
            !isAopProxy && hasAroundStereotype(methodAnnotationMetadata) ||
            hasDeclaredAroundAdvice(methodAnnotationMetadata) && !classElement.isAbstract()) {
            if (methodElement.isFinal()) {
                if (hasDeclaredAroundAdvice(methodAnnotationMetadata)) {
                    throw new ProcessingException(methodElement, "Method defines AOP advice but is declared final. Change the method to be non-final in order for AOP advice to be applied.");
                } else if (aopDefinedOnClassAndPublicMethod && isDeclaredInThisClass(methodElement)) {
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

}
