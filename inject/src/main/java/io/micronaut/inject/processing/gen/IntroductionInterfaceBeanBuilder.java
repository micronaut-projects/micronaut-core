package io.micronaut.inject.processing.gen;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionVisitor;

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
        for (MethodElement methodElement : classElement.getEnclosedElements(ElementQuery.ALL_METHODS.includeHiddenElements().includeOverriddenMethods())) {
            if (aopHelper.visitIntrospectedMethod(aopProxyWriter, classElement, methodElement)) {
                continue;
            }
//            else if (methodElement.hasStereotype(Executable.class)) {
//                boolean preprocess = methodElement.isTrue(Executable.class, "processOnStartup");
//                if (preprocess) {
//                    aopProxyWriter.setRequiresMethodProcessing(true);
//                }
//                if (methodElement.isAccessible(classElement)) {
//                    aopProxyWriter.visitExecutableMethod(classElement, methodElement, visitorContext);
//                }
//            }
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
