package io.micronaut.python.processing.annotation;

import io.micronaut.inject.ast.annotation.AbstractElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.python.processing.visitor.ElementDef;
import io.micronaut.python.processing.visitor.DecoratorDef;

/**
 * Factory for creating and managing annotation metadata for Python elements.
 * <p>
 * This class extends {@link AbstractElementAnnotationMetadataFactory} and provides
 * support for reading and building annotation metadata based on Python decorators.
 * The factory delegates annotation processing logic to {@link PythonAnnotationMetadataBuilder}.
 * </p>
 * <p>
 * The metadata factory may be used in either a read-only or mutable context, depending
 * on how it is constructed. To enforce immutability, use {@link #readOnly()} to obtain a read-only factory.
 * </p>
 *
 * <p>Typical usage:</p>
 * <pre>
 *     PythonAnnotationMetadataBuilder builder = ...;
 *     PythonElementAnnotationMetadataFactory factory =
 *         new PythonElementAnnotationMetadataFactory(false, builder);
 *     ElementAnnotationMetadata metadata = factory.build(someElement);
 * </pre>
 *
 * @since 5.0.0
 */
public class PythonElementAnnotationMetadataFactory extends AbstractElementAnnotationMetadataFactory<ElementDef, DecoratorDef> {

    /**
     * Constructs a new factory for Python element annotation metadata.
     *
     * @param isReadOnly             Whether the factory should operate in read-only mode (no shared cache modifications).
     * @param annotationMetadataBuilder The annotation metadata builder used to introspect Python decorators.
     */
    public PythonElementAnnotationMetadataFactory(boolean isReadOnly, PythonAnnotationMetadataBuilder annotationMetadataBuilder) {
        super(isReadOnly, annotationMetadataBuilder);
    }

    /**
     * Creates a read-only version of this element annotation metadata factory.
     * No modifications to annotation metadata will be persisted within the shared cache.
     *
     * @return a read-only element annotation metadata factory
     */
    @Override
    public ElementAnnotationMetadataFactory readOnly() {
        return new PythonElementAnnotationMetadataFactory(true, (PythonAnnotationMetadataBuilder) metadataBuilder);
    }
}
