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
package io.micronaut.inject.ast.annotation;

import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArgumentUtils;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Mutable annotation metadata.
 *
 * @param <R> The return type
 * @author Denis Stepanov
 * @since 4.0.0
 */
public interface MutableAnnotationMetadataDelegate<R> extends AnnotationMetadataDelegate {

    /**
     * The empty metadata.
     */
    MutableAnnotationMetadataDelegate<?> EMPTY = new MutableAnnotationMetadataDelegate<>() {
    };

    /**
     * Annotate this element with the given annotation type. If the annotation is already present then
     * any values populated by the builder will be merged/overridden with the existing values.
     *
     * @param annotationType The annotation type
     * @param consumer A function that receives the {@link AnnotationValueBuilder}
     * @param <T> The annotation generic type
     * @return This element
     */
    default <T extends Annotation> R annotate(String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        throw new UnsupportedOperationException("Element of type [" + getClass() + "] does not support adding annotations at compilation time");
    }

    /**
     * Removes an annotation of the given type from the element.
     *
     * <p>If the annotation features any stereotypes these will also be removed unless there are other
     * annotations that reference the stereotype to be removed.</p>
     *
     * <p>In the case of repeatable annotations this method will remove all repeated annotations, effectively
     * clearing out all declared repeated annotations of the given type.</p>
     *
     * @param annotationType The annotation type
     * @return This element
     */
    default R removeAnnotation(String annotationType) {
        throw new UnsupportedOperationException("Element of type [" + getClass() + "] does not support removing annotations at compilation time");
    }

    /**
     * @see #removeAnnotation(String)
     * @param annotationType The annotation type
     * @param <T> The annotation generic type
     * @return This element
     */
    default <T extends Annotation> R removeAnnotation(Class<T> annotationType) {
        return removeAnnotation(Objects.requireNonNull(annotationType).getName());
    }

    /**
     * Removes all annotations that pass the given predicate.
     * @param predicate The predicate
     * @param <T> The annotation generic type
     * @return This element
     */
    default <T extends Annotation> R removeAnnotationIf(Predicate<AnnotationValue<T>> predicate) {
        throw new UnsupportedOperationException("Element of type [" + getClass() + "] does not support removing annotations at compilation time");
    }

    /**
     * Removes a stereotype of the given name from the element.
     * @param annotationType The annotation type
     * @return This element
     */
    default R removeStereotype(String annotationType) {
        throw new UnsupportedOperationException("Element of type [" + getClass() + "] does not support removing annotations at compilation time");
    }

    /**
     * Removes a stereotype annotation of the given type from the element.
     * @param annotationType The annotation type
     * @param <T> The annotation generic type
     * @return This element
     */
    default <T extends Annotation> R removeStereotype(Class<T> annotationType) {
        return removeStereotype(Objects.requireNonNull(annotationType).getName());
    }

    /**
     * Annotate this element with the given annotation type. If the annotation is already present then
     * any values populated by the builder will be merged/overridden with the existing values.
     *
     * @param annotationType The annotation type
     * @return This element
     */
    default R annotate(String annotationType) {
        return annotate(annotationType, annotationValueBuilder -> { });
    }

    /**
     * Annotate this element with the given annotation type. If the annotation is already present then
     * any values populated by the builder will be merged/overridden with the existing values.
     *
     * @param annotationType The annotation type
     * @param consumer A function that receives the {@link AnnotationValueBuilder}
     * @param <T> The annotation generic type
     * @return This element
     */
    default <T extends Annotation> R annotate(Class<T> annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
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
     * @return This element
     */
    default <T extends Annotation> R annotate(Class<T> annotationType) {
        ArgumentUtils.requireNonNull("annotationType", annotationType);
        return annotate(annotationType.getName(), annotationValueBuilder -> { });
    }

    /**
     * Annotate this element with the given annotation type. If the annotation is already present then
     * any values populated by the builder will be merged/overridden with the existing values.
     *
     * @param annotationValue The annotation type
     * @param <T> The annotation generic type
     * @return This element
     * @since 3.0.0
     */
    default <T extends Annotation> R annotate(AnnotationValue<T> annotationValue) {
        throw new UnsupportedOperationException("Element of type [" + getClass() + "] does not support adding annotations at compilation time");
    }

    /**
     * The annotations the source wrote on this element, type use or type variable, in source order and as
     * written.
     *
     * <p>Unlike the metadata returned by {@link #getAnnotationMetadata()}, this is the declaration as the
     * compiler sees it: a repeatable annotation written once is itself, a container the source wrote is the
     * container, two or more repetitions arrive in the container the compiler synthesizes for them, as the
     * class file would carry them, and nothing an {@link io.micronaut.inject.annotation.AnnotationMapper},
     * {@link io.micronaut.inject.annotation.AnnotationRemapper},
     * {@link io.micronaut.inject.annotation.AnnotationTransformer} or a visitor's {@code annotate(...)} added
     * appears, nor any stereotype. Each value carries the annotation interface's retention and its defaults for
     * the members the use left out, empty strings and arrays included.</p>
     *
     * <p>For a type variable it is what was written at this use of the variable (nothing, if the use wrote
     * nothing), or the annotations of the type parameter declaration when the element comes from
     * {@link io.micronaut.inject.ast.ClassElement#getDeclaredGenericPlaceholders()} or
     * {@link io.micronaut.inject.ast.MethodElement#getDeclaredTypeVariables()}. It is empty for an element no
     * source backs, such as one created by reflection or {@code ClassElement.of(String)}.</p>
     *
     * @return The annotations as written, or an empty list
     * @since 5.3.0
     */
    @Experimental
    @NonNull
    default List<AnnotationValue<?>> getSourceAnnotations() {
        return List.of();
    }
}
