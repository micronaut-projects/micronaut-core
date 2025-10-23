package io.micronaut.python.processing.visitor;

import io.micronaut.inject.ast.ArrayableClassElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.python.processing.PythonProcessingEnvironment;

import java.util.List;
import java.util.Objects;

public final class PythonClassElement extends AbstractPythonElement implements ArrayableClassElement {
    private final int arrayDimensions;
    private final PythonProcessingEnvironment environment;

    public PythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment) {
        this(classDef, environment, 0);
    }

    public PythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment, int arrayDimensions) {
        super(
            Objects.requireNonNull(classDef, "ClassDef cannot be null").name(),
            classDef,
            Objects.requireNonNull(environment).metadataFactory()
        );
        this.environment = environment;
        this.arrayDimensions = arrayDimensions;
    }

    @Override
    public ClassDef getNativeType() {
        return (ClassDef) super.getNativeType();
    }

    @Override
    public String toString() {
        return "Python Class: " + getNativeType().name();
    }

    @Override
    public <T extends Element> List<T> getEnclosedElements(ElementQuery<T> query) {
        return List.of();
    }

    @Override
    public boolean isAssignable(String type) {
        if (getName().equals(type)) {
            return true;
        }
        if (getNativeType().bases().contains(type)) {
            return true;
        }
        for (String base : getNativeType().bases()) {
            PythonClassElement baseElement = environment.classes().get(base);
            if (baseElement != null && baseElement.isAssignable(type)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        return new PythonClassElement(getNativeType(), environment, arrayDimensions);
    }

    @Override
    public boolean isArray() {
        return arrayDimensions > 0;
    }

    @Override
    public int getArrayDimensions() {
        return arrayDimensions;
    }
}
