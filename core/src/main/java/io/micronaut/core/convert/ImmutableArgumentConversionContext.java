/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.core.convert;

import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;

import java.util.Map;

/**
 * Immutable variant of {@link io.micronaut.core.convert.ArgumentConversionContext} that can be used as a constant
 * in cases where conversion error handling and rejection is not required.
 *
 * @param <T> The generic type
 * @since 3.2.7
 * @author graemerocher
 */
public interface ImmutableArgumentConversionContext<T> extends ArgumentConversionContext<T> {

    /**
     * Create a new simple {@link ConversionContext} for the given generic type variables.
     *
     * <p>NOTE: The instance returned by this method is NOT thread safe and should be shared
     * via static state or between threads.</p>
     *
     * @param <T>      type Generic
     * @param argument The argument
     * @return The conversion context
     * @since 3.2.7
     */
    static <T> ImmutableArgumentConversionContext<T> of(Argument<T> argument) {
        ArgumentUtils.requireNonNull("argument", argument);
        return () -> argument;
    }

    /**
     * Create a simple {@link ConversionContext} for the given generic type variables.
     *
     * <p>NOTE: The instance returned by this method is NOT thread safe and should be shared
     * via static state or between threads.</p>
     *
     * @param <T>      type Generic
     * @param type The argument
     * @return The conversion context
     * @since 3.2.7
     */
    static <T> ImmutableArgumentConversionContext<T> of(Class<T> type) {
        return of(Argument.of(type));
    }
}
