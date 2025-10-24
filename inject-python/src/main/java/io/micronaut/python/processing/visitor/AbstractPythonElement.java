package io.micronaut.python.processing.visitor;

import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.annotation.AbstractAnnotationElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;

import java.util.Objects;

abstract sealed class AbstractPythonElement extends AbstractAnnotationElement implements Element permits AbstractPythonClassElement, PythonFieldElement, PythonMethodElement, PythonParameterElement {
    private final String name;
    private final Object nativeType;

    protected AbstractPythonElement(String name, Object nativeType, ElementAnnotationMetadataFactory metadataFactory) {
        super(metadataFactory);
        this.name = name;
        this.nativeType = nativeType;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isProtected() {
        return false;
    }

    @Override
    public boolean isPublic() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AbstractPythonElement that)) {
            return false;
        }
        return Objects.equals(name, that.name) && Objects.equals(nativeType, that.nativeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, nativeType);
    }

    @Override
    public Object getNativeType() {
        return nativeType;
    }
}
