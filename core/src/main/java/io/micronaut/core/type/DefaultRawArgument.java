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

/**
 * An argument for a type written without its type arguments: {@code List} rather than {@code List<String>}.
 *
 * <p>The type parameters are kept as the declaring type declares them, so that a raw usage reads the same as
 * one written with the declaring type's own variables, and only {@link #isRawType()} tells the two apart.</p>
 *
 * @param <T> The argument type
 * @since 5.2.0
 */
@Internal
final class DefaultRawArgument<T> extends DefaultArgument<T> {

    @Nullable
    private final String argumentName;

    DefaultRawArgument(Class<T> type,
                       @Nullable String name,
                       @Nullable AnnotationMetadata annotationMetadata,
                       Argument<?> @Nullable [] typeParameters) {
        super(type, name, annotationMetadata, typeParameters);
        this.argumentName = name;
    }

    @Override
    public boolean isRawType() {
        return true;
    }

    @Override
    public Argument<T> withName(@Nullable String name) {
        return new DefaultRawArgument<>(getType(), name, getAnnotationMetadata(), getTypeParameters());
    }

    @Override
    public Argument<T> withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return new DefaultRawArgument<>(getType(), argumentName, annotationMetadata, getTypeParameters());
    }
}
