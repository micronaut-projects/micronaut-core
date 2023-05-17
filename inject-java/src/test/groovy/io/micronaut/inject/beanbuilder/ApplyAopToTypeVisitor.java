package io.micronaut.inject.beanbuilder;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;

public class ApplyAopToTypeVisitor implements TypeElementVisitor<Object, Object> {

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.getSimpleName().equals("Test")) {
            AnnotationValue<Annotation> av = AnnotationValue
                    .builder("aopbuilder.Mutating")
                    .value("name")
                    .build();
            context.getClassElement(ApplyAopToMe.class)
                    .ifPresent((applyAopToMe) -> element
                            .addAssociatedBean(applyAopToMe)
                            .intercept(av)
                            .inject()
                    );
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
