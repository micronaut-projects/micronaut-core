/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.ast;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.util.ArgumentUtils;

import javax.annotation.Nonnull;
import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Stores data about a compile time element. The underlying object can be a class, field, or method.
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface Element extends AnnotationMetadata {

    /**
     * @return The name of the element.
     */
    String getName();

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
    Object getNativeType();

    /**
     * Annotate this element with the given annotation type. If the annotation is already present then
     * any values populated by the builder will be merged/overridden with the existing values.
     *
     * @param annotationType The annotation type
     * @param consumer A function that receives the {@link AnnotationValueBuilder}
     * @param <T> The annotation generic type
     * @return This element
     */
    @Nonnull
    default <T extends Annotation> Element annotate(@Nonnull String annotationType, @Nonnull Consumer<AnnotationValueBuilder<T>> consumer) {
        throw new UnsupportedOperationException("Element of type [" + getClass() + "] does not support adding annotations at compilation time");
    }

    /**
     * Annotate this element with the given annotation type. If the annotation is already present then
     * any values populated by the builder will be merged/overridden with the existing values.
     *
     * @param annotationType The annotation type
     * @param <T> The annotation generic type
     * @return This element
     */
    @Nonnull
    default <T extends Annotation> Element annotate(@Nonnull String annotationType) {
        return annotate(annotationType, (Consumer<AnnotationValueBuilder<T>>) annotationValueBuilder -> {});
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
    @Nonnull
    default <T extends Annotation> Element annotate(@Nonnull Class<T> annotationType, @Nonnull Consumer<AnnotationValueBuilder<T>> consumer) {
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
    @Nonnull
    default <T extends Annotation> Element annotate(@Nonnull Class<T> annotationType) {
        ArgumentUtils.requireNonNull("annotationType", annotationType);
        return annotate(annotationType.getName(), annotationValueBuilder -> { });
    }

    /**
     * The simple name of the element. For a class this will be the name without the package.
     *
     * @return The simple name
     */
    default String getSimpleName() {
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
