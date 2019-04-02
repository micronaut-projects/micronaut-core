package io.micronaut.inject.visitor;

import io.micronaut.core.version.annotation.Version;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;

// tests that dynamic annotation works
public class AnnotatingVisitor implements TypeElementVisitor<Version, Version> {

    public static final String ANN_NAME = TestAnn.class.getName();

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        context.info("Annotating type", element);
        element.annotate(TestAnn.class, (builder) -> builder.value("class"));
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        context.info("Annotating method", element);
        element.annotate(TestAnn.class, (builder) -> builder.value("method"));
    }

    @Override
    public void visitConstructor(ConstructorElement element, VisitorContext context) {
        context.info("Annotating constructor", element);
        element.annotate(TestAnn.class, (builder) -> builder.value("constructor"));
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        context.info("Annotating field", element);
        // test using name
        element.annotate(TestAnn.class.getName(), (builder) -> builder.value("field"));
    }
}
