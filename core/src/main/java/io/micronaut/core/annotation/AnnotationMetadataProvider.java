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
package io.micronaut.core.annotation;

import java.lang.annotation.Annotation;
import java.util.Optional;

/**
 * An interface for a type that provides {@link AnnotationMetadata}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface AnnotationMetadataProvider extends AnnotationSource {

    /**
     * Supplies the metadata. Defaults to {@link AnnotationMetadata#EMPTY_METADATA}.
     *
     * @return The {@link AnnotationMetadata}
     */
    @NonNull
    default AnnotationMetadata getAnnotationMetadata() {
        return AnnotationMetadata.EMPTY_METADATA;
    }

    @Nullable
    @Override
    default <T extends Annotation> T synthesize(@NonNull Class<T> annotationClass) {
        return getAnnotationMetadata().synthesize(annotationClass);
    }

    @NonNull
    @Override
    default Annotation[] synthesizeAll() {
        return getAnnotationMetadata().synthesizeAll();
    }

    @NonNull
    @Override
    default Annotation[] synthesizeDeclared() {
        return getAnnotationMetadata().synthesizeDeclared();
    }

    @Override
    default boolean isAnnotationPresent(@NonNull Class<? extends Annotation> annotationClass) {
        return getAnnotationMetadata().isAnnotationPresent(annotationClass);
    }

    @Override
    default boolean isDeclaredAnnotationPresent(@NonNull Class<? extends Annotation> annotationClass) {
        return getAnnotationMetadata().isDeclaredAnnotationPresent(annotationClass);
    }

    @Nullable
    @Override
    default <T extends Annotation> T synthesizeDeclared(@NonNull Class<T> annotationClass) {
        return getAnnotationMetadata().synthesizeDeclared(annotationClass);
    }

    @NonNull
    @Override
    default <T extends Annotation> T[] synthesizeAnnotationsByType(@NonNull Class<T> annotationClass) {
        return getAnnotationMetadata().synthesizeAnnotationsByType(annotationClass);
    }

    @NonNull
    @Override
    default <T extends Annotation> T[] synthesizeDeclaredAnnotationsByType(@NonNull Class<T> annotationClass) {
        return getAnnotationMetadata().synthesizeDeclaredAnnotationsByType(annotationClass);
    }

    @NonNull
    @Override
    default <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(@NonNull String annotation) {
        return getAnnotationMetadata().findAnnotation(annotation);
    }

    @NonNull
    @Override
    default <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(@NonNull Class<T> annotationClass) {
        return getAnnotationMetadata().findAnnotation(annotationClass);
    }

    @NonNull
    @Override
    default <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(@NonNull String annotation) {
        return getAnnotationMetadata().findDeclaredAnnotation(annotation);
    }

    @NonNull
    @Override
    default <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(@NonNull Class<T> annotationClass) {
        return getAnnotationMetadata().findDeclaredAnnotation(annotationClass);
    }

    @Override
    default AnnotationSource getTargetAnnotationMetadata() {
        return getAnnotationMetadata().getTargetAnnotationMetadata();
    }
}
