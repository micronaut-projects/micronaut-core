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

import io.micronaut.core.annotation.NonNull;

/**
 * Extends {@link Argument} to allow differentiating the
 * variable name from the argument name in cases where this is
 * required (parameters and fields for example).
 *
 * @param <T> The generic type
 * @since 3.2.0
 */
public interface GenericPlaceholder<T> extends Argument<T> {

    /**
     * @return The variable name, never {@code null}.
     */
    @NonNull
    default String getVariableName() {
        return getName();
    }

    @Override
    default boolean isTypeVariable() {
        return true;
    }
}
