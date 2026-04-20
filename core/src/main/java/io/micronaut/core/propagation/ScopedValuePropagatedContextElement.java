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
package io.micronaut.core.propagation;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

/**
 * A {@link PropagatedContextElement} that exposes a {@link ScopedValue} binding to be used while
 * the context is in scope.
 *
 * @param <T> The type held by the {@link ScopedValue}.
 * @author Denis Stepanov
 * @since 5.0.0
 */
@Experimental
public interface ScopedValuePropagatedContextElement<T> extends PropagatedContextElement {

    /**
     * Returns the scoped value that should be bound.
     *
     * @return The scoped value to bind
     */
    ScopedValue<T> scopedValue();

    /**
     * Returns the value to bind to the scoped value while the context is in scope.
     *
     * @return Value bound to the scoped value
     */
    @Nullable
    T scopedValueValue();
}
