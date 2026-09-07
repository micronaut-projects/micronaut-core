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
import io.micronaut.core.util.ArrayUtils;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

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

    private static final Argument<?>[] OBJECT_BOUND = {Argument.OBJECT_ARGUMENT};

    private final Argument<?>[] upperBounds;
    private final Argument<?>[] lowerBounds;

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
        super(type, name, annotationMetadata, true, typeParameters);
        this.upperBounds = ArrayUtils.isEmpty(upperBounds) ? OBJECT_BOUND : upperBounds;
        this.lowerBounds = ArrayUtils.isEmpty(lowerBounds) ? Argument.ZERO_ARGUMENTS : lowerBounds;
    }

    @Override
    public List<Argument<?>> getUpperBounds() {
        return Arrays.asList(upperBounds);
    }

    @Override
    public List<Argument<?>> getLowerBounds() {
        return Arrays.asList(lowerBounds);
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
        return new DefaultWildcardArgument<>(getType(), getName(), annotationMetadata, getTypeParameters(), upperBounds, lowerBounds);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("?");
        if (lowerBounds.length > 0) {
            builder.append(" super ").append(lowerBounds[0]);
        } else if (upperBounds != OBJECT_BOUND) {
            builder.append(" extends ").append(upperBounds[0]);
        }
        return builder.append(' ').append(getName()).toString();
    }
}
