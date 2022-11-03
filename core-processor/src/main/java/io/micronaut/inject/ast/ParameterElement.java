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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Represents a parameter to a method or constructor.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface ParameterElement extends TypedElement {

    /**
     * @return The type of the parameter
     */
    @Override
    @NonNull
    ClassElement getType();

    @NonNull
    @Override
    default String getDescription(boolean simple) {
        if (simple) {
            return getType().getSimpleName() + " " + getName();
        } else {
            return getType().getName() + " " + getName();
        }
    }

    /**
     * Return method associated with this parameter.
     *
     * @return The method element
     */
    default MethodElement getMethodElement() {
        throw new IllegalStateException("Method element is not supported!");
    }

    @Override
    default ParameterElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (ParameterElement) TypedElement.super.withAnnotationMetadata(annotationMetadata);
    }

    /**
     * Creates a parameter element for a simple type and name.
     *
     * @param type The type
     * @param name The name
     * @return The parameter element
     */
    static @NonNull ParameterElement of(@NonNull Class<?> type, @NonNull String name) {
        return of(ClassElement.of(type), name);
    }

    /**
     * Creates a parameter element for the given arguments.
     *
     * @param type The element type
     * @param name The name
     * @return The parameter element
     * @since 2.4.0
     */
    static @NonNull ParameterElement of(
        @NonNull ClassElement type,
        @NonNull String name) {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        return new ReflectParameterElement(type, name);
    }

    /**
     * Creates a parameter element for the given arguments.
     *
     * @param type                       The element type
     * @param name                       The name
     * @param annotationMetadataProvider The name
     * @param metadataBuilder            The name
     * @return The parameter element
     * @since 4.0.0
     */
    static @NonNull ParameterElement of(
        @NonNull ClassElement type,
        @NonNull String name,
        @NonNull AnnotationMetadataProvider annotationMetadataProvider,
        @NonNull AbstractAnnotationMetadataBuilder<?, ?> metadataBuilder) {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        return new ReflectParameterElement(type, name) {

            private AnnotationMetadata annotationMetadata;

            @Override
            public ParameterElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
                return of(type, name, new AnnotationMetadataProvider() {
                    @Override
                    public AnnotationMetadata getAnnotationMetadata() {
                        return annotationMetadata;
                    }
                }, metadataBuilder);
            }

            @Override
            public <T extends Annotation> Element annotate(@NonNull String annotationType, @NonNull Consumer<AnnotationValueBuilder<T>> consumer) {
                ArgumentUtils.requireNonNull("annotationType", annotationType);
                AnnotationValueBuilder<T> builder = AnnotationValue.builder(annotationType);
                //noinspection ConstantConditions
                if (consumer != null) {

                    consumer.accept(builder);
                    AnnotationValue<T> av = builder.build();
                    this.annotationMetadata = metadataBuilder.annotate(getAnnotationMetadata(), av);
                }
                return this;
            }

            @Override
            public AnnotationMetadata getAnnotationMetadata() {
                if (annotationMetadata == null) {
                    annotationMetadata = annotationMetadataProvider.getAnnotationMetadata();
                }
                return annotationMetadata;
            }

            @Override
            public <T extends Annotation> Element annotate(AnnotationValue<T> annotationValue) {
                ArgumentUtils.requireNonNull("annotationValue", annotationValue);
                annotationMetadata = metadataBuilder.annotate(getAnnotationMetadata(), annotationValue);
                return this;
            }

            @Override
            public Element removeAnnotation(@NonNull String annotationType) {
                ArgumentUtils.requireNonNull("annotationType", annotationType);
                annotationMetadata = metadataBuilder.removeAnnotation(getAnnotationMetadata(), annotationType);
                return this;
            }

            @Override
            public <T extends Annotation> Element removeAnnotationIf(@NonNull Predicate<AnnotationValue<T>> predicate) {
                ArgumentUtils.requireNonNull("predicate", predicate);
                annotationMetadata = metadataBuilder.removeAnnotationIf(getAnnotationMetadata(), predicate);
                return this;

            }

            @Override
            public Element removeStereotype(@NonNull String annotationType) {
                ArgumentUtils.requireNonNull("annotationType", annotationType);
                annotationMetadata = metadataBuilder.removeStereotype(getAnnotationMetadata(), annotationType);
                return this;
            }

        };
    }
}
