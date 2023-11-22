package io.micronaut.inject.beanbuilder;

import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

public class TestMultipleFactoryDefiningVisitor implements TypeElementVisitor<Prototype, Object> {
    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.hasAnnotation(Prototype.class)) {

            context.getClassElement(OtherBeanProducer.class)
                    .ifPresent((producer) -> {
                        final BeanElementBuilder beanElementBuilder = element.addAssociatedBean(producer)
                                .qualifier(AnnotationValue.builder(Primary.class).build());
                        final ElementQuery<MethodElement> query = ElementQuery.ALL_METHODS
                                .annotated((am) -> am.hasAnnotation(TestProduces.class));
                        beanElementBuilder.produceBeans(query, (builder) -> {
                            builder.annotate("test.Foo");
                            builder.withParameters(params ->
                                params[0].injectValue("primary")
                            ).qualifier(AnnotationValue.builder(Primary.class).build());
                            }
                        );

                        final BeanElementBuilder beanElementBuilder2 = element.addAssociatedBean(producer)
                                .qualifier("other");
                        beanElementBuilder2.produceBeans(query, (builder) -> {
                            builder.annotate("test.Bar");
                            builder.withParameters(params ->
                                params[0].injectValue("other")
                            ).qualifier("other");
                        });
                    });
        }
    }


    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
