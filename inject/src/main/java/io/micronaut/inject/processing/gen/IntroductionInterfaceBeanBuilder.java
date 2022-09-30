package io.micronaut.inject.processing.gen;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionVisitor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class IntroductionInterfaceBeanBuilder extends AbstractBeanBuilder {

    private final String factoryBeanDefinitionName;

    IntroductionInterfaceBeanBuilder(ClassElement classElement, VisitorContext visitorContext, String factoryBeanDefinitionName) {
        super(classElement, visitorContext);
        this.factoryBeanDefinitionName = factoryBeanDefinitionName;
    }

    @Override
    public void build() {
        BeanDefinitionVisitor aopProxyWriter = aopHelper.createIntroductionAopProxyWriter(classElement, metadataBuilder, visitorContext);
        aopProxyWriter.visitTypeArguments(classElement.getAllTypeArguments());
        MethodElement constructorElement = classElement.getPrimaryConstructor().orElse(null);
        if (constructorElement != null) {
            aopProxyWriter.visitBeanDefinitionConstructor(constructorElement, constructorElement.isReflectionRequired(), visitorContext);
        } else {
            aopProxyWriter.visitDefaultConstructor(classElement, visitorContext);
        }
        if (factoryBeanDefinitionName != null) {
            aopProxyWriter.visitSuperBeanDefinitionFactory(factoryBeanDefinitionName);
        }

        // The introduction will include overridden methods* (find(List) <- find(Iterable)*) but ordinary class introduction doesn't
        // Because of the caching we need to process declared methods first
        Set<MethodElement> processed = new HashSet<>();
        List<MethodElement> declaredEnclosedElements = classElement.getEnclosedElements(ElementQuery.ALL_METHODS.includeHiddenElements().includeOverriddenMethods().onlyDeclared());
        for (MethodElement methodElement : declaredEnclosedElements) {
            aopHelper.visitIntrospectedMethod(aopProxyWriter, classElement, methodElement);
            processed.add(methodElement);
        }
        List<MethodElement> otherEnclosedElements = classElement.getEnclosedElements(ElementQuery.ALL_METHODS.includeHiddenElements().includeOverriddenMethods());
        for (MethodElement methodElement : otherEnclosedElements) {
            if (processed.contains(methodElement)) {
                continue;
            }
            aopHelper.visitIntrospectedMethod(aopProxyWriter, classElement, methodElement);
        }

        if (classElement.hasAnnotation(ANN_REQUIRES_VALIDATION)) {
            if (ConfigurationPropertiesBeanBuilder.isConfigurationProperties(classElement)) {
                // Configuration beans are validated at the startup and don't require validation advice
                aopProxyWriter.setValidated(true);
            } else {
                for (MethodElement methodElement : classElement.getEnclosedElements(ElementQuery.ALL_METHODS.annotated(am -> am.hasAnnotation(ANN_REQUIRES_VALIDATION)))) {
                    methodElement.annotate(AbstractBeanBuilder.ANN_VALIDATED);
                }
            }
        }
        beanDefinitionWriters.add(aopProxyWriter);
    }

}
