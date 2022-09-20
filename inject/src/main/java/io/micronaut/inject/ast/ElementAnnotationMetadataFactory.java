package io.micronaut.inject.ast;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;

import java.util.function.Function;

/**
 * Element's annotation metadata factory.
 *
 * @author Denis Stepanov
 * @since 3.8.0
 */
public interface ElementAnnotationMetadataFactory {

    /**
     * Build new element annotation metadata from the element.
     *
     * @param element The element
     * @return the element's metadata
     */
    @NonNull
    ElementAnnotationMetadata build(@NonNull Element element);

    /**
     * Build new element annotation metadata from the element with preloaded annotations.
     * This method will avoid fetching default annotation metadata from cache.
     *
     * @param element            The element
     * @param annotationMetadata The preloaded annotation
     * @return the element's metadata
     */
    @NonNull
    ElementAnnotationMetadata build(@NonNull Element element, @NonNull AnnotationMetadata annotationMetadata);

    default ElementAnnotationMetadataFactory readOnly() {
        throw new IllegalStateException("Unsupported operation!");
    }

    default ElementAnnotationMetadataFactory overrideForNativeType(Object nativeType,
                                                                   Function<Element, ElementAnnotationMetadata> fn) {
        ElementAnnotationMetadataFactory thisFactory = this;
        return new ElementAnnotationMetadataFactory() {

            private boolean fetched;

            @Override
            public ElementAnnotationMetadata build(Element element) {
                if (!fetched && element.getNativeType().equals(nativeType)) {
                    fetched = true;
                    return fn.apply(element);
                }
                return thisFactory.build(element);
            }

            @Override
            public ElementAnnotationMetadata build(Element element, AnnotationMetadata annotationMetadata) {
                return thisFactory.build(element, annotationMetadata);
            }
        };
    }
}
