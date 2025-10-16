package io.micronaut.python.processing.visitor;

import java.util.Objects;

public final class PythonClassElement extends AbstractPythonElement {
    public PythonClassElement(ClassDef classDef) {
        super(Objects.requireNonNull(classDef, "ClassDef cannot be null").name(), classDef);
    }

    @Override
    public ClassDef getNativeType() {
        return (ClassDef) super.getNativeType();
    }

    @Override
    public String toString() {
        return "Python Class: " + getNativeType().name();
    }
}
