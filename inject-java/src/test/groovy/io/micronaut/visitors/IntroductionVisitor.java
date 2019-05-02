package io.micronaut.visitors;

import io.micronaut.aop.introduction.Stub;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.ArrayList;
import java.util.List;

public class IntroductionVisitor implements TypeElementVisitor<Stub, Object> {

    public static List<String> VISITED_ELEMENTS = new ArrayList<>();
    public static List<ClassElement> VISITED_CLASS_ELEMENTS = new ArrayList<>();
    public static List<MethodElement> VISITED_METHOD_ELEMENTS = new ArrayList<>();

    @Override
    public void start(VisitorContext visitorContext) {
        VISITED_ELEMENTS.clear();
        VISITED_CLASS_ELEMENTS.clear();
        VISITED_METHOD_ELEMENTS.clear();
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        visit(element);
        VISITED_CLASS_ELEMENTS.add(element);
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        VISITED_METHOD_ELEMENTS.add(element);
        visit(element);
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        visit(element);
    }

    private void visit(Element element) {
        VISITED_ELEMENTS.add(element.getName());
    }
}
