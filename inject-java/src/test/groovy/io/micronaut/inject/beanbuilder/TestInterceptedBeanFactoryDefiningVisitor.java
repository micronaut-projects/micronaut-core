package io.micronaut.inject.beanbuilder;

import io.micronaut.aop.Around;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import org.jspecify.annotations.NonNull;

public class TestInterceptedBeanFactoryDefiningVisitor implements TypeElementVisitor<Prototype, Object> {

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.hasAnnotation(Prototype.class)) {
            context.getClassElement(InterceptedTestBeanProducer.class)
                .ifPresent(producer -> {
                    BeanElementBuilder beanElementBuilder = element.addAssociatedBean(producer);
                    ElementQuery<MethodElement> query = ElementQuery.ALL_METHODS
                        .annotated(am -> am.hasAnnotation(TestProduces.class));
                    beanElementBuilder.produceBeans(query, builder -> builder.intercept(
                        AnnotationValue.builder(Around.class)
                            .member("proxyTarget", true)
                            .member("lazy", true)
                            .build()
                    ));
                });
        }
    }

    @Override
    public @NonNull VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
