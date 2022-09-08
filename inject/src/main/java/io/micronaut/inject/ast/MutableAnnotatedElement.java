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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArgumentUtils;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Mutable annotation metadata.
 *
 * @param <Rtr> The return type
 * @author Denis Stepanov
 * @since 4.0.0
 */
public interface MutableAnnotatedElement<Rtr> {

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
    default <T extends Annotation> Rtr annotate(@NonNull String annotationType, @NonNull Consumer<AnnotationValueBuilder<T>> consumer) {
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
    default Rtr removeAnnotation(@NonNull String annotationType) {
        throw new UnsupportedOperationException("Element of type [" + getClass() + "] does not support removing annotations at compilation time");
    }

    /**
     * @see #removeAnnotation(String)
     * @param annotationType The annotation type
     * @param <T> The annotation generic type
     * @return This element
     */
    default <T extends Annotation> Rtr removeAnnotation(@NonNull Class<T> annotationType) {
        return removeAnnotation(Objects.requireNonNull(annotationType).getName());
    }

    /**
     * Removes all annotations that pass the given predicate.
     * @param predicate The predicate
     * @param <T> The annotation generic type
     * @return This element
     */
    default <T extends Annotation> Rtr removeAnnotationIf(@NonNull Predicate<AnnotationValue<T>> predicate) {
        throw new UnsupportedOperationException("Element of type [" + getClass() + "] does not support removing annotations at compilation time");
    }

    /**
     * Removes a stereotype of the given name from the element.
     * @param annotationType The annotation type
     * @return This element
     */
    default Rtr removeStereotype(@NonNull String annotationType) {
        throw new UnsupportedOperationException("Element of type [" + getClass() + "] does not support removing annotations at compilation time");
    }

    /**
     * Removes a stereotype annotation of the given type from the element.
     * @param annotationType The annotation type
     * @param <T> The annotation generic type
     * @return This element
     */
    default <T extends Annotation> Rtr removeStereotype(@NonNull Class<T> annotationType) {
        return removeStereotype(Objects.requireNonNull(annotationType).getName());
    }

    /**
     * Annotate this element with the given annotation type. If the annotation is already present then
     * any values populated by the builder will be merged/overridden with the existing values.
     *
     * @param annotationType The annotation type
     * @return This element
     */
    @NonNull
    default Rtr annotate(@NonNull String annotationType) {
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
    @NonNull
    default <T extends Annotation> Rtr annotate(@NonNull Class<T> annotationType, @NonNull Consumer<AnnotationValueBuilder<T>> consumer) {
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
    @NonNull
    default <T extends Annotation> Rtr annotate(@NonNull Class<T> annotationType) {
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
    @NonNull
    default <T extends Annotation> Rtr annotate(@NonNull AnnotationValue<T> annotationValue) {
        throw new UnsupportedOperationException("Element of type [" + getClass() + "] does not support adding annotations at compilation time");
    }

}
