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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

import java.lang.annotation.Annotation;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Mutable annotation metadata provider.
 *
 * @param <R> The return type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public interface ElementMutableAnnotationMetadataDelegate<R> extends MutableAnnotationMetadataDelegate<R> {

    /**
     * Provides the return type instance.
     *
     * @return the return instance
     */
    R getReturnInstance();

    @Override
    @NonNull
    MutableAnnotationMetadataDelegate<?> getAnnotationMetadata();

    @Override
    @NonNull
    default <T extends Annotation> R annotate(@NonNull String annotationType, @NonNull Consumer<AnnotationValueBuilder<T>> consumer) {
        getAnnotationMetadata().annotate(annotationType, consumer);
        return getReturnInstance();
    }

    @Override
    @NonNull
    default R removeAnnotation(@NonNull String annotationType) {
        getAnnotationMetadata().removeAnnotation(annotationType);
        return getReturnInstance();
    }

    @Override
    @NonNull
    default <T extends Annotation> R removeAnnotation(@NonNull Class<T> annotationType) {
        getAnnotationMetadata().removeAnnotation(annotationType);
        return getReturnInstance();
    }

    @Override
    @NonNull
    default <T extends Annotation> R removeAnnotationIf(@NonNull Predicate<AnnotationValue<T>> predicate) {
        getAnnotationMetadata().removeAnnotationIf(predicate);
        return getReturnInstance();
    }

    @Override
    @NonNull
    default R removeStereotype(@NonNull String annotationType) {
        getAnnotationMetadata().removeStereotype(annotationType);
        return getReturnInstance();
    }

    @Override
    @NonNull
    default <T extends Annotation> R removeStereotype(@NonNull Class<T> annotationType) {
        getAnnotationMetadata().removeStereotype(annotationType);
        return getReturnInstance();
    }

    @Override
    @NonNull
    default R annotate(@NonNull String annotationType) {
        getAnnotationMetadata().annotate(annotationType);
        return getReturnInstance();
    }

    @Override
    @NonNull
    default <T extends Annotation> R annotate(@NonNull Class<T> annotationType, @NonNull Consumer<AnnotationValueBuilder<T>> consumer) {
        getAnnotationMetadata().annotate(annotationType, consumer);
        return getReturnInstance();
    }

    @Override
    @NonNull
    default <T extends Annotation> R annotate(@NonNull Class<T> annotationType) {
        getAnnotationMetadata().annotate(annotationType);
        return getReturnInstance();
    }

    @Override
    @NonNull
    default <T extends Annotation> R annotate(@NonNull AnnotationValue<T> annotationValue) {
        getAnnotationMetadata().annotate(annotationValue);
        return getReturnInstance();
    }

}
