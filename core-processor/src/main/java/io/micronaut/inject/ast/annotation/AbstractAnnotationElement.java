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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.lang.annotation.Annotation;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * An abstract element.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public abstract class AbstractAnnotationElement implements io.micronaut.inject.ast.Element {

    protected final ElementAnnotationMetadataFactory elementAnnotationMetadataFactory;
    @Nullable
    protected AnnotationMetadata presetAnnotationMetadata;
    @Nullable
    private ElementAnnotationMetadata elementAnnotationMetadata;

    /**
     * @param annotationMetadataFactory The annotation metadata factory
     */
    protected AbstractAnnotationElement(ElementAnnotationMetadataFactory annotationMetadataFactory) {
        this.elementAnnotationMetadataFactory = annotationMetadataFactory;
    }

    @Override
    public @NonNull AnnotationMetadata getAnnotationMetadata() {
        return getElementAnnotationMetadata();
    }

    public final ElementAnnotationMetadataFactory getElementAnnotationMetadataFactory() {
        return elementAnnotationMetadataFactory;
    }

    /**
     * @return The element's annotation metadata
     */
    protected ElementAnnotationMetadata getElementAnnotationMetadata() {
        if (elementAnnotationMetadata == null) {
            if (presetAnnotationMetadata == null) {
                elementAnnotationMetadata = elementAnnotationMetadataFactory.build(this);
            } else {
                elementAnnotationMetadata = elementAnnotationMetadataFactory.buildMutable(presetAnnotationMetadata);
            }
        }
        return elementAnnotationMetadata;
    }

    /**
     * Get annotation metadata to add or remove annotations.
     *
     * @return The annotation metadata to write
     */
    protected MutableAnnotationMetadataDelegate<?> getAnnotationMetadataToWrite() {
        return getElementAnnotationMetadata();
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.@NonNull Element annotate(@NonNull String annotationType, @NonNull Consumer<AnnotationValueBuilder<T>> consumer) {
        getAnnotationMetadataToWrite().annotate(annotationType, consumer);
        return this;
    }

    @Override
    public io.micronaut.inject.ast.@NonNull Element removeAnnotation(@NonNull String annotationType) {
        getAnnotationMetadataToWrite().removeAnnotation(annotationType);
        return this;
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.@NonNull Element removeAnnotation(@NonNull Class<T> annotationType) {
        getAnnotationMetadataToWrite().removeAnnotation(annotationType);
        return this;
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.@NonNull Element removeAnnotationIf(@NonNull Predicate<AnnotationValue<T>> predicate) {
        getAnnotationMetadataToWrite().removeAnnotationIf(predicate);
        return this;
    }

    @Override
    public io.micronaut.inject.ast.@NonNull Element removeStereotype(@NonNull String annotationType) {
        getAnnotationMetadataToWrite().removeStereotype(annotationType);
        return this;
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.@NonNull Element removeStereotype(@NonNull Class<T> annotationType) {
        getAnnotationMetadataToWrite().removeStereotype(annotationType);
        return this;
    }

    @Override
    public io.micronaut.inject.ast.@NonNull Element annotate(@NonNull String annotationType) {
        getAnnotationMetadataToWrite().annotate(annotationType);
        return this;
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.@NonNull Element annotate(@NonNull Class<T> annotationType, @NonNull Consumer<AnnotationValueBuilder<T>> consumer) {
        getAnnotationMetadataToWrite().annotate(annotationType, consumer);
        return this;
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.@NonNull Element annotate(@NonNull Class<T> annotationType) {
        getAnnotationMetadataToWrite().annotate(annotationType);
        return this;
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.@NonNull Element annotate(@NonNull AnnotationValue<T> annotationValue) {
        getAnnotationMetadataToWrite().annotate(annotationValue);
        return this;
    }
}
