/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.ast;

import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.util.ArgumentUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Stores data about a compile time element. The underlying object can be a class, field, or method.
 *
 * @author James Kleeh
 * @author graemerocher
 * @since 1.0
 */
public interface Element extends AnnotationMetadataDelegate, AnnotatedElement {
    /**
     * An empty array of elements.
     * @since 2.1.1
     */
    Element[] EMPTY_ELEMENT_ARRAY = new Element[0];

    /**
     * @return The name of the element.
     */
    @Override
    @NonNull String getName();

    /**
     * @return True if the element is package private.
     * @since 2.3.0
     */
    default boolean isPackagePrivate() {
        return false;
    }

    /**
     * @return True if the element is protected.
     */
    boolean isProtected();

    /**
     * @return True if the element is public.
     */
    boolean isPublic();

    /**
     * Returns the native underlying type. This API is extended by all of the inject language implementations.
     * The object returned by this method will be the language native type the information is being retrieved from.
     *
     * @return The native type
     */
    @NonNull Object getNativeType();

    /**
     * Annotate this element with the given annotation type. If the annotation is already present then
     * any values populated by the builder will be merged/overridden with the existing values.
     *
     * @param annotationType The annotation type
     * @param consumer A function that receives the {@link AnnotationValueBuilder}
     * @param <T> The annotation generic type
     * @return This element
     */
    @NonNull
    default <T extends Annotation> Element annotate(@NonNull String annotationType, @NonNull Consumer<AnnotationValueBuilder<T>> consumer) {
        throw new UnsupportedOperationException("Element of type [" + getClass() + "] does not support adding annotations at compilation time");
    }

    /**
     * Annotate this element with the given annotation type. If the annotation is already present then
     * any values populated by the builder will be merged/overridden with the existing values.
     *
     * @param annotationType The annotation type
     * @return This element
     */
    @NonNull
    default Element annotate(@NonNull String annotationType) {
        return annotate(annotationType, annotationValueBuilder -> { });
    }

    /**
     * Annotate this element with the given annotation type. If the annotation is already present then
     * any values populated by the builder will be merged/overridden with the existing values.
     *
     * @param annotationType The annotation type
     * @param consumer A function that receives the {@link AnnotationValueBuilder}
     * @param <T> The annotation generic type
     * @return The {@link AnnotationValueBuilder}
     */
    @NonNull
    default <T extends Annotation> Element annotate(@NonNull Class<T> annotationType, @NonNull Consumer<AnnotationValueBuilder<T>> consumer) {
        ArgumentUtils.requireNonNull("annotationType", annotationType);
        ArgumentUtils.requireNonNull("consumer", consumer);
        return annotate(annotationType.getName(), consumer);
    }

    /**
     * Annotate this element with the given annotation type. If the annotation is already present then
     * any values populated by the builder will be merged/overridden with the existing values.
     *
     * @param annotationType The annotation type
     * @param <T> The annotation generic type
     * @return The {@link AnnotationValueBuilder}
     */
    @NonNull
    default <T extends Annotation> Element annotate(@NonNull Class<T> annotationType) {
        ArgumentUtils.requireNonNull("annotationType", annotationType);
        return annotate(annotationType.getName(), annotationValueBuilder -> { });
    }

    /**
     * The simple name of the element. For a class this will be the name without the package.
     *
     * @return The simple name
     */
    default @NonNull String getSimpleName() {
        return getName();
    }

    /**
     * @return True if the element is abstract.
     */
    default boolean isAbstract() {
        return false;
    }

    /**
     * @return True if the element is static.
     */
    default boolean isStatic() {
        return false;
    }

    /**
     * @return The documentation, if any.
     */
    default Optional<String> getDocumentation() {
        return Optional.empty();
    }

    /**
     * @return True if the element is private.
     */
    default boolean isPrivate() {
        return !isPublic();
    }

    /**
     * @return True if the element is final.
     */
    default boolean isFinal() {
        return false;
    }
}
