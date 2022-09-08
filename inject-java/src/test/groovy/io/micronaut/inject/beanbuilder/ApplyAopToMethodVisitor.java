package io.micronaut.inject.beanbuilder;

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;

public class ApplyAopToMethodVisitor implements TypeElementVisitor<Object, Object> {

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.hasAnnotation(AnnotationUtil.SINGLETON)){

            AnnotationValue<Annotation> av = AnnotationValue
                    .builder("aopbuilder.Mutating")
                    .value("name")
                    .build();
            context.getClassElement(ApplyAopToMe.class)
                    .ifPresent((applyAopToMe) -> element
                            .addAssociatedBean(applyAopToMe)
                            .inject()
                            .withMethods(ElementQuery.ALL_METHODS.named(n -> n.equals("hello")), method ->
                                method.intercept(av)
                            ));
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}

