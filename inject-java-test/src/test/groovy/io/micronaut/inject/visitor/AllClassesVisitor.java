package io.micronaut.inject.visitor;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;

import java.util.ArrayList;
import java.util.List;

public class AllClassesVisitor implements TypeElementVisitor<Object, Object> {

    private List<MethodElement> visitedMethodElements = new ArrayList<>();
    private List<ClassElement> visitedClassElements = new ArrayList<>();

    public AllClassesVisitor() {
        reset();
    }

    public void reset() {
        visitedMethodElements.clear();
        visitedClassElements.clear();
    }

    public List<MethodElement> getVisitedMethodElements() {
        return visitedMethodElements;
    }

    public List<ClassElement> getVisitedClassElements() {
        return visitedClassElements;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        visitedClassElements.add(element);
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        visitedMethodElements.add(element);
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
    }
}

