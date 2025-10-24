package io.micronaut.python.processing.visitor;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.EnumElement;
import io.micronaut.python.processing.PythonProcessingEnvironment;

import java.util.List;

/**
 * Represents a Python enum class element in the Micronaut injection context.
 * <p>
 * This class extends {@link AbstractPythonClassElement} and implements {@link EnumElement}
 * to provide enum-specific functionality for Python enums parsed from AST.
 * </p>
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
public final class PythonEnumElement extends AbstractPythonClassElement implements EnumElement {

    /**
     * Creates a new PythonEnumElement for the given class definition.
     *
     * @param classDef The class definition from Python AST
     * @param environment The Python processing environment
     */
    public PythonEnumElement(ClassDef classDef, PythonProcessingEnvironment environment) {
        super(classDef, environment);
    }

    @Override
    public boolean isEnum() {
        return true;
    }

    /**
     * Creates a new PythonEnumElement for the given class definition with array dimensions.
     *
     * @param classDef The class definition from Python AST
     * @param environment The Python processing environment
     * @param arrayDimensions The number of array dimensions
     */
    public PythonEnumElement(ClassDef classDef, PythonProcessingEnvironment environment, int arrayDimensions) {
        super(classDef, environment, arrayDimensions);
    }

    @Override
    protected ClassElement createWithArrayDimensions(int arrayDimensions) {
        return new PythonEnumElement(getNativeType(), environment, arrayDimensions);
    }

    @Override
    public ClassDef getNativeType() {
        return (ClassDef) super.getNativeType();
    }

    @Override
    public String toString() {
        return "Python Enum: " + getNativeType().name();
    }

    @Override
    public <T extends Element> List<T> getEnclosedElements(ElementQuery<T> query) {
        return List.of();
    }

    @Override
    public List<String> values() {
        return getNativeType().values();
    }

    @Override
    public boolean isAssignable(String type) {
        return getName().equals(type);
    }
}
