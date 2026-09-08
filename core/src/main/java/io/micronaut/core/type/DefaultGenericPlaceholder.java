/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.core.type;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Implementation of {@link GenericPlaceholder}.
 *
 * @param <T> The generic type
 * @since 3.2.0
 */
@Internal
final class DefaultGenericPlaceholder<T>
        extends DefaultArgument<T>
        implements GenericPlaceholder<T> {
    @Nullable
    private final String name;
    @Nullable
    private final String variableName;
    /**
     * The declared bounds, or {@code null} when they were not recorded.
     */
    @Nullable
    private final List<Argument<?>> bounds;

    /**
     * Constructor for where@author variable name and argument name are@author same.
     *
     * @param type               The type
     * @param name               The name
     * @param annotationMetadata The annotation metadata
     * @param genericTypes       The generic types
     */
    DefaultGenericPlaceholder(
            Class<T> type,
            @Nullable String name,
            @Nullable AnnotationMetadata annotationMetadata,
            Argument<?> @Nullable ... genericTypes) {
        this(type, name, name, annotationMetadata, genericTypes, (List<Argument<?>>) null);
    }

    /**
     * Constructor for where@author variable name and argument name differ.
     *
     * @param type               The type
     * @param name               The name
     * @param variableName       The variable name
     * @param annotationMetadata The annotation metadata
     * @param genericTypes       The generic types
     */
    DefaultGenericPlaceholder(
            Class<T> type,
            @Nullable String name,
            String variableName,
            @Nullable AnnotationMetadata annotationMetadata,
            Argument<?>... genericTypes) {
        this(type, name, variableName, annotationMetadata, genericTypes, (List<Argument<?>>) null);
    }

    /**
     * Constructor for a placeholder that keeps the bounds declared for its type variable.
     *
     * @param type               The type
     * @param name               The name
     * @param variableName       The variable name, {@code null} when it is the argument name
     * @param annotationMetadata The annotation metadata
     * @param genericTypes       The generic types
     * @param bounds             The declared bounds, {@code null} or empty when they were not recorded
     * @since 5.2.0
     */
    DefaultGenericPlaceholder(
            Class<T> type,
            @Nullable String name,
            @Nullable String variableName,
            @Nullable AnnotationMetadata annotationMetadata,
            Argument<?> @Nullable [] genericTypes,
            Argument<?> @Nullable [] bounds) {
        this(type, name, variableName, annotationMetadata, genericTypes,
            bounds == null || bounds.length == 0 ? null : List.of(bounds));
    }

    private DefaultGenericPlaceholder(
            Class<T> type,
            @Nullable String name,
            @Nullable String variableName,
            @Nullable AnnotationMetadata annotationMetadata,
            Argument<?> @Nullable [] genericTypes,
            @Nullable List<Argument<?>> bounds) {
        super(type, name, annotationMetadata, genericTypes);
        this.name = name;
        this.variableName = variableName;
        this.bounds = bounds;
    }

    @Override
    public List<Argument<?>> getBounds() {
        return bounds != null ? bounds : GenericPlaceholder.super.getBounds();
    }

    @Override
    public String getVariableName() {
        return variableName != null ? variableName : getName();
    }

    @Override
    public boolean isTypeVariable() {
        return true;
    }

    @Override
    public Argument<T> withName(@Nullable String name) {
        return new DefaultGenericPlaceholder<>(getType(), name, variableName, getAnnotationMetadata(), getTypeParameters(), bounds);
    }

    @Override
    public Argument<T> withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return new DefaultGenericPlaceholder<>(getType(), name, variableName, annotationMetadata, getTypeParameters(), bounds);
    }
}
