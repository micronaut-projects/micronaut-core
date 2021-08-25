package io.micronaut.inject.beanbuilder;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

public class TestBeanFactoryDefiningVisitor implements TypeElementVisitor<Prototype, Object> {

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.hasAnnotation(Prototype.class)){

            context.getClassElement(TestBeanProducer.class)
                    .ifPresent((producer) -> {
                        final BeanElementBuilder beanElementBuilder = element.addAssociatedBean(producer);
                        final ElementQuery<MethodElement> query = ElementQuery.ALL_METHODS
                                .annotated((am) -> am.hasAnnotation(TestProduces.class));
                        beanElementBuilder.produceBeans(query);

                        final ElementQuery<FieldElement> fields = ElementQuery.ALL_FIELDS
                                .annotated((am) -> am.hasAnnotation(TestProduces.class));
                        beanElementBuilder.produceBeans(fields);
                    });
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
