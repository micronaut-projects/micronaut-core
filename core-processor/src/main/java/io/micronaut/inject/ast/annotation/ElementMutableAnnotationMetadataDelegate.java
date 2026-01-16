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
    MutableAnnotationMetadataDelegate<?> getAnnotationMetadata();

    @Override
    default <T extends Annotation> R annotate(String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        getAnnotationMetadata().annotate(annotationType, consumer);
        return getReturnInstance();
    }

    @Override
    default R removeAnnotation(String annotationType) {
        getAnnotationMetadata().removeAnnotation(annotationType);
        return getReturnInstance();
    }

    @Override
    default <T extends Annotation> R removeAnnotation(Class<T> annotationType) {
        getAnnotationMetadata().removeAnnotation(annotationType);
        return getReturnInstance();
    }

    @Override
    default <T extends Annotation> R removeAnnotationIf(Predicate<AnnotationValue<T>> predicate) {
        getAnnotationMetadata().removeAnnotationIf(predicate);
        return getReturnInstance();
    }

    @Override
    default R removeStereotype(String annotationType) {
        getAnnotationMetadata().removeStereotype(annotationType);
        return getReturnInstance();
    }

    @Override
    default <T extends Annotation> R removeStereotype(Class<T> annotationType) {
        getAnnotationMetadata().removeStereotype(annotationType);
        return getReturnInstance();
    }

    @Override
    default R annotate(String annotationType) {
        getAnnotationMetadata().annotate(annotationType);
        return getReturnInstance();
    }

    @Override
    default <T extends Annotation> R annotate(Class<T> annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        getAnnotationMetadata().annotate(annotationType, consumer);
        return getReturnInstance();
    }

    @Override
    default <T extends Annotation> R annotate(Class<T> annotationType) {
        getAnnotationMetadata().annotate(annotationType);
        return getReturnInstance();
    }

    @Override
    default <T extends Annotation> R annotate(AnnotationValue<T> annotationValue) {
        getAnnotationMetadata().annotate(annotationValue);
        return getReturnInstance();
    }

}
