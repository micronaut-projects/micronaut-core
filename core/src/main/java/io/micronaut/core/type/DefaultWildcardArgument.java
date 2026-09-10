/*
 * Copyright 2017-2026 original authors
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
import java.util.Objects;

/**
 * Default implementation of {@link WildcardArgument}. It is also a {@link GenericPlaceholder} named after the
 * type parameter the wildcard stands for, because that is what the processors emitted for a wildcard before the
 * bounds were recorded, and callers that resolve placeholders keep seeing the same argument.
 *
 * @param <T> The type the wildcard is bounded by
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Internal
final class DefaultWildcardArgument<T> extends DefaultArgument<T> implements WildcardArgument<T>, GenericPlaceholder<T> {

    private static final List<Argument<?>> OBJECT_BOUND = List.of(Argument.OBJECT_ARGUMENT);

    @Nullable
    private final String name;
    private final List<Argument<?>> upperBounds;
    private final List<Argument<?>> lowerBounds;

    /**
     * @param type               The type the wildcard is bounded by
     * @param name               The name of the type parameter the wildcard stands for
     * @param annotationMetadata The annotation metadata
     * @param typeParameters     The type parameters of the type
     * @param upperBounds        The upper bounds, {@code null} or empty for {@code Object}
     * @param lowerBounds        The lower bounds, {@code null} or empty for none
     */
    DefaultWildcardArgument(Class<T> type,
                            @Nullable String name,
                            @Nullable AnnotationMetadata annotationMetadata,
                            Argument<?> @Nullable [] typeParameters,
                            Argument<?> @Nullable [] upperBounds,
                            Argument<?> @Nullable [] lowerBounds) {
        this(type,
            name,
            annotationMetadata,
            typeParameters,
            upperBounds == null || upperBounds.length == 0 ? OBJECT_BOUND : List.of(upperBounds),
            lowerBounds == null || lowerBounds.length == 0 ? List.of() : List.of(lowerBounds));
    }

    private DefaultWildcardArgument(Class<T> type,
                                    @Nullable String name,
                                    @Nullable AnnotationMetadata annotationMetadata,
                                    Argument<?> @Nullable [] typeParameters,
                                    List<Argument<?>> upperBounds,
                                    List<Argument<?>> lowerBounds) {
        super(type, name, annotationMetadata, true, typeParameters);
        this.name = name;
        this.upperBounds = upperBounds;
        this.lowerBounds = lowerBounds;
    }

    @Override
    public List<Argument<?>> getUpperBounds() {
        return upperBounds;
    }

    @Override
    public List<Argument<?>> getLowerBounds() {
        return lowerBounds;
    }

    @Override
    public boolean isTypeVariable() {
        return true;
    }

    @Override
    public Argument<T> withName(@Nullable String name) {
        return new DefaultWildcardArgument<>(getType(), name, getAnnotationMetadata(), getTypeParameters(), upperBounds, lowerBounds);
    }

    @Override
    public Argument<T> withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return new DefaultWildcardArgument<>(getType(), name, annotationMetadata, getTypeParameters(), upperBounds, lowerBounds);
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o)
            && o instanceof DefaultWildcardArgument<?> that
            && upperBounds.equals(that.upperBounds)
            && lowerBounds.equals(that.lowerBounds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), upperBounds, lowerBounds);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("?");
        if (!lowerBounds.isEmpty()) {
            builder.append(" super ").append(lowerBounds.get(0));
        } else if (upperBounds != OBJECT_BOUND) {
            builder.append(" extends ").append(upperBounds.get(0));
        }
        if (name != null) {
            builder.append(' ').append(name);
        }
        return builder.toString();
    }
}
