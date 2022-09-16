package io.micronaut.inject.processing.gen;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionVisitor;

public class AopProxySupportedBeanBuilder extends SimpleBeanBuilder {

    private final boolean isAopProxy;
    private BeanDefinitionVisitor aopProxyVisitor;

    protected AopProxySupportedBeanBuilder(ClassElement classElement, VisitorContext visitorContext, boolean isAopProxy) {
        super(classElement, visitorContext);
        this.isAopProxy = isAopProxy;
    }

    @Override
    protected void build(BeanDefinitionVisitor visitor) {
        super.build(visitor);
    }

    private BeanDefinitionVisitor getAopProxyVisitor(BeanDefinitionVisitor visitor, MethodElement methodElement) {
        if (aopProxyVisitor == null) {
            aopProxyVisitor = aopHelper.createAopProxyWriter(
                visitor,
                isAopProxy ? classElement.getAnnotationMetadata() : methodElement.getAnnotationMetadata(),
                metadataBuilder,
                visitorContext,
                isAopProxy? false : true
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
        boolean aopDefinedOnClassAndPublicMethod = isAopProxy && methodElement.isPublic();
        if (aopDefinedOnClassAndPublicMethod || hasAroundStereotype(methodElement) && !classElement.isAbstract()) {
            if (methodElement.isFinal()) {
                if (hasDeclaredAroundAdvice(methodElement)) {
                    throw new ProcessingException(methodElement, "Method defines AOP advice but is declared final. Change the method to be non-final in order for AOP advice to be applied.");
                } else if (aopDefinedOnClassAndPublicMethod && isDeclaredInThisClass(methodElement)) {
                    throw new ProcessingException(methodElement, "Public method inherits AOP advice but is declared final. Either make the method non-public or apply AOP advice only to public methods declared on the class.");
                }
            } else {
                BeanDefinitionVisitor aopProxyVisitor = getAopProxyVisitor(visitor, methodElement);
                aopHelper.visitAroundMethod(aopProxyVisitor, classElement, methodElement);
                return true;
            }
        }
        return false;
    }

}
