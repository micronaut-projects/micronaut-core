package io.micronaut.python.processing.visitor;

import io.micronaut.inject.ast.ArrayableClassElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.python.processing.PythonProcessingEnvironment;

import java.util.List;

public sealed abstract class AbstractPythonClassElement extends AbstractPythonElement implements ArrayableClassElement permits PythonClassElement, PythonEnumElement {
    protected final int arrayDimensions;
    protected final PythonProcessingEnvironment environment;

    protected AbstractPythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment) {
        this(classDef, environment, 0);
    }

    protected AbstractPythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment, int arrayDimensions) {
        super(
            classDef.name(),
            classDef,
            environment.metadataFactory()
        );
        this.environment = environment;
        this.arrayDimensions = arrayDimensions;
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        return createWithArrayDimensions(arrayDimensions);
    }

    protected abstract ClassElement createWithArrayDimensions(int arrayDimensions);

    @Override
    public boolean isArray() {
        return arrayDimensions > 0;
    }

    @Override
    public int getArrayDimensions() {
        return arrayDimensions;
    }
}
