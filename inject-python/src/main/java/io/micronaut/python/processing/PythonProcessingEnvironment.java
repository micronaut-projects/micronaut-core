package io.micronaut.python.processing;

import io.micronaut.python.processing.annotation.PythonAnnotationMetadataBuilder;
import io.micronaut.python.processing.annotation.PythonElementAnnotationMetadataFactory;
import io.micronaut.python.processing.visitor.ClassDef;
import io.micronaut.python.processing.visitor.PythonClassElement;
import io.micronaut.python.processing.visitor.PythonVisitorContext;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents a Python processing environment that extends the basic Python environment with additional
 * processing capabilities for annotation metadata and visitor context. This record is used during
 * Micronaut's annotation processing phase to handle Python code integration.
 *
 * @param environment The underlying Python environment containing classes and decorators.
 * @param annotationMetadataBuilder The builder for creating annotation metadata from Python elements.
 * @param metadataFactory The factory for creating element annotation metadata.
 * @param visitorContext The visitor context for processing Python elements.
 * @since 4.8.0
 * @author Micronaut
 */
public record PythonProcessingEnvironment(
    PythonEnvironment environment,
    PythonAnnotationMetadataBuilder annotationMetadataBuilder,
    PythonElementAnnotationMetadataFactory metadataFactory,
    PythonVisitorContext visitorContext
) implements AutoCloseable {

    /**
     * Closes the Python processing environment by closing the underlying Python environment.
     */
    @Override
    public void close() {
        environment.close();
    }

    /**
     * Creates a Python processing environment with the specified Python environment.
     * The annotation metadata builder, metadata factory, and visitor context will be initialized automatically.
     *
     * @param environment The Python environment to use.
     */
    public PythonProcessingEnvironment(PythonEnvironment environment) {
        this(
            environment,
            null,
            null,
            null
        );
    }

    public PythonProcessingEnvironment {
        Objects.requireNonNull(environment, "Python environment cannot be null");

        if (visitorContext == null) {
            visitorContext = new PythonVisitorContext(environment.decorators());
        }
        if (annotationMetadataBuilder == null) {
            annotationMetadataBuilder = visitorContext.getAnnotationMetadataBuilder();
        }
        if (metadataFactory == null) {
            metadataFactory = visitorContext.getElementAnnotationMetadataFactory();
        }
    }

    /**
     * Returns a map of Python class elements, converting the raw class definitions into
     * processing-ready elements with annotation metadata support.
     *
     * @return A map of class names to Python class elements.
     */
    public Map<String, PythonClassElement> classes() {
        return toMapOfClassElement(environment.classes(), this);
    }

    private static Map<String, PythonClassElement> toMapOfClassElement(Map<String, ClassDef> classes, PythonProcessingEnvironment environment) {
        return classes.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> new PythonClassElement(
                    entry.getValue(),
                    environment
                )
            ));
    }
}
