package io.micronaut.inject.processing.gen;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionVisitor;

final class IntroductionBeanBuilder extends AbstractBeanBuilder {

    IntroductionBeanBuilder(ClassElement classElement, VisitorContext visitorContext) {
        super(classElement, visitorContext);
    }

    @Override
    public void build() {
        BeanDefinitionVisitor aopProxyWriter = aopHelper.createIntroductionAdviceWriter(classElement, metadataBuilder, visitorContext);
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
