package io.micronaut.python.processing;

import io.micronaut.python.processing.annotation.PythonAnnotationMetadataBuilder;
import io.micronaut.python.processing.annotation.PythonElementAnnotationMetadataFactory;
import io.micronaut.python.processing.visitor.ClassDef;
import io.micronaut.python.processing.visitor.PythonClassElement;
import io.micronaut.python.processing.visitor.PythonVisitorContext;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public record PythonProcessingEnvironment(
    PythonEnvironment environment,
    PythonAnnotationMetadataBuilder annotationMetadataBuilder,
    PythonElementAnnotationMetadataFactory metadataFactory,
    PythonVisitorContext visitorContext
) {

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
