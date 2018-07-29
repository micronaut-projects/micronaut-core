/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.core.annotation;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.Optional;

/**
 * <p>A source of annotations. This API provides an alternative to Java's {@link java.lang.reflect.AnnotatedElement} that uses the compile time produced data
 *  from Micronaut. This is the parent interface of the {@link AnnotationMetadata} which provides event more methods to read annotations values and compute {@link java.lang.annotation.Repeatable} annotations.</p>
 *
 *  <p>Note that this interface also includes methods such as {@link #synthesize(Class)} that allows materializing an instance of an annotation by producing a runtime proxy. These methods are a last resort if no other option is possible and should generally be avoided as they require the use of runtime reflection and proxying which hurts performance and memory consumption.</p>
 *
 * @see AnnotationMetadata
 * @author Graeme Rocher
 * @since 1.0
 */
public interface AnnotationSource {
    /**
     * An empty annotation source.
     */
    AnnotationSource EMPTY = new AnnotationSource() {
    };

    /**
     * Synthesizes a new annotation from the metadata for the given annotation type. This method works
     * by creating a runtime proxy of the annotation interface and should be avoided in favour of
     * direct use of the annotation metadata and only used for unique cases that require integrating third party libraries.
     *
     * @param annotationClass The annotation class
     * @param <T>             The annotation generic type
     * @return The annotation or null if it doesn't exist
     */
    default <T extends Annotation> T synthesize(Class<T> annotationClass) {
        return null;
    }

    /**
     * Synthesizes a new annotation from the metadata for the given annotation type. This method works
     * by creating a runtime proxy of the annotation interface and should be avoided in favour of
     * direct use of the annotation metadata and only used for unique cases that require integrating third party libraries.
     * <p>
     * This method ignores inherited annotations. (Returns null if no
     * annotations are directly present on this element.)
     *
     * @param annotationClass The annotation class
     * @param <T>             The annotation generic type
     * @return The annotation or null if it doesn't exist
     */
    default <T extends Annotation> T synthesizeDeclared(Class<T> annotationClass) {
        return null;
    }

    /**
     * Synthesizes a new annotations from the metadata. This method works
     * by creating a runtime proxy of the annotation interface and should be avoided in favour of
     * direct use of the annotation metadata and only used for unique cases that require integrating third party libraries.
     *
     * @return All the annotations
     */
    default Annotation[] synthesizeAll() {
        return AnnotationUtil.ZERO_ANNOTATIONS;
    }

    /**
     * Synthesizes a new annotations from the metadata. This method works
     * by creating a runtime proxy of the annotation interface and should be avoided in favour of
     * direct use of the annotation metadata and only used for unique cases that require integrating third party libraries.
     *
     * @return All declared annotations
     */
    default Annotation[] synthesizeDeclared() {
        return AnnotationUtil.ZERO_ANNOTATIONS;
    }

    /**
     * Synthesizes a new annotations from the metadata for the given type. This method works
     * by creating a runtime proxy of the annotation interface and should be avoided in favour of
     * direct use of the annotation metadata and only used for unique cases that require integrating third party libraries.
     *
     * @param annotationClass The annotation type
     * @param <T> The annotation generic type
     * @return All annotations by the given type
     */
    @SuppressWarnings("unchecked")
    default <T extends Annotation> T[] synthesizeAnnotationsByType(Class<T> annotationClass) {
        return (T[]) Array.newInstance(annotationClass, 0);
    }

    /**
     * Synthesizes a new annotations from the metadata for the given type. This method works
     * by creating a runtime proxy of the annotation interface and should be avoided in favour of
     * direct use of the annotation metadata and only used for unique cases that require integrating third party libraries.
     *
     * @param annotationClass The annotation type
     * @param <T> The annotation generic type
     * @return Declared annotations by the given type
     */
    @SuppressWarnings("unchecked")
    default <T extends Annotation> T[] synthesizeDeclaredAnnotationsByType(Class<T> annotationClass) {
        return (T[]) Array.newInstance(annotationClass, 0);
    }

    /**
     * Find an {@link AnnotationValue} for the given annotation name.
     *
     * @param annotation The annotation name
     * @param <T> The annotation type
     * @return A {@link AnnotationValue} instance
     */
    default <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(String annotation) {
        return Optional.empty();
    }

    /**
     * Find an {@link AnnotationValue} for the given annotation type.
     *
     * @param annotation The annotation
     * @param <T> The annotation type
     * @return A {@link AnnotationValue} instance
     */
    default <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(Class<T> annotation) {
        return Optional.empty();
    }

    /**
     * Get all of the values for the given annotation that are directly declared on the annotated element.
     *
     * @param annotation The annotation name
     * @param <T> The annotation type
     * @return A {@link AnnotationValue} instance
     */
    default <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(String annotation) {
        return Optional.empty();
    }

    /**
     * Get all of the values for the given annotation that are directly declared on the annotated element.
     *
     * @param annotation The annotation name
     * @param <T> The annotation type
     * @return A {@link AnnotationValue} instance
     */
    default <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(Class<T> annotation) {
        return Optional.empty();
    }

    /**
     * Find an {@link AnnotationValue} for the given annotation name.
     *
     * @param annotation The annotation name
     * @param <T> The annotation type
     * @return A {@link AnnotationValue} instance or null
     */
    default @Nullable <T extends Annotation> AnnotationValue<T> getAnnotation(String annotation) {
        return this.<T>findAnnotation(annotation).orElse(null);
    }

    /**
     * Find an {@link AnnotationValue} for the given annotation name.
     *
     * @param annotation The annotation name
     * @param <T> The annotation type
     * @return A {@link AnnotationValue} instance or null
     */
    default @Nullable <T extends Annotation> AnnotationValue<T> getAnnotation(Class<T> annotation) {
        return this.findAnnotation(annotation).orElse(null);
    }

    /**
     * Get all of the values for the given annotation that are directly declared on the annotated element.
     *
     * @param annotation The annotation name
     * @param <T> The annotation type
     * @return A {@link AnnotationValue} instance
     */
    default @Nullable <T extends Annotation> AnnotationValue<T> getDeclaredAnnotation(String annotation) {
        return this.<T>findDeclaredAnnotation(annotation).orElse(null);
    }

    /**
     * Find an {@link AnnotationValue} for the given annotation name.
     *
     * @param annotation The annotation name
     * @param <T> The annotation type
     * @return A {@link AnnotationValue} instance or null
     */
    default @Nullable <T extends Annotation> AnnotationValue<T> getDeclaredAnnotation(Class<T> annotation) {
        return this.findDeclaredAnnotation(annotation).orElse(null);
    }
    /**
     * Return whether an annotation is present.
     *
     * @param annotationClass The annotation class
     * @return True if it is
     */
    default boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return false;
    }

    /**
     * Variation of {@link #isAnnotationPresent(Class)} for declared annotations.
     *
     * @param annotationClass The annotation class
     * @return True if it is
     */
    default boolean isDeclaredAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return false;
    }
}
