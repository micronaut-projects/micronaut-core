package io.micronaut.visitors;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.inject.visitor.*;

import java.util.ArrayList;
import java.util.List;

public class ControllerGetVisitor implements TypeElementVisitor<Controller, Get> {

    public static List<String> VISITED_ELEMENTS = new ArrayList<>();

    @Override
    public void visitClass(ClassElement element, AnnotationMetadata annotationMetadata, VisitorContext context) {
        visit(element);
    }

    @Override
    public void visitMethod(MethodElement element, AnnotationMetadata annotationMetadata, VisitorContext context) {
        visit(element);
    }

    @Override
    public void visitField(FieldElement element, AnnotationMetadata annotationMetadata, VisitorContext context) {
        visit(element);
    }

    private void visit(Element element) {
        VISITED_ELEMENTS.add(element.getName());
    }
}
