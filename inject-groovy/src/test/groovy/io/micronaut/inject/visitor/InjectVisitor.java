package io.micronaut.inject.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class InjectVisitor implements TypeElementVisitor<Object, Inject> {
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
