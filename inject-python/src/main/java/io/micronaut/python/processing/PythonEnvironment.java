package io.micronaut.python.processing;

import io.micronaut.python.processing.visitor.ClassDef;
import io.micronaut.python.processing.visitor.DecoratorDef;

import java.util.Map;

public record PythonEnvironment(
    Map<String, ClassDef> classes,
    Map<String, DecoratorDef> decorators
) {
}
