/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.core.graal;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;

/**
 * Internal interface to allow integration with Graal Polyglot languages that marks a type as boxing or
 * wrapping a foreign value of another language.
 *
 * <p>NOTE: regarded as internal and not for public consumption.</p>
 *
 * @param <T> The underlying type, usually a Truffle {@code Value}.
 *
 * @since 5.0.0
 */
@Internal
@Experimental
public interface Boxed<T> {
    /**
     * Unboxes the type.
     *
     * @return The unboxed type.
     */
    @SuppressWarnings("checkstyle:MethodName")
    T $unbox();
}
