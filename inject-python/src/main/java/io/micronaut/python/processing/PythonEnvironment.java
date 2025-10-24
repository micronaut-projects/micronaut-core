package io.micronaut.python.processing;

import io.micronaut.python.processing.visitor.ClassDef;
import io.micronaut.python.processing.visitor.DecoratorDef;
import org.graalvm.polyglot.Context;

import java.util.Map;

/**
 * Represents a Python environment containing parsed Python classes, decorators, and the GraalVM Polyglot context.
 * This record holds the state of a Python module after parsing, providing access to class definitions and decorators.
 *
 * @param classes A map of Python class names to their definitions.
 * @param decorators A map of Python decorator names to their definitions.
 * @param context The GraalVM Polyglot context used for executing Python code.
 * @since 4.8.0
 * @author Micronaut
 */
public record PythonEnvironment(
    Map<String, ClassDef> classes,
    Map<String, DecoratorDef> decorators,
    Context context
) implements AutoCloseable {

    @Override
    public void close() {
        if (context != null) {
            context.close();
        }
    }
}
