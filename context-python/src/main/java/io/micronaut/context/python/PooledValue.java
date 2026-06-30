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
package io.micronaut.context.python;

import io.micronaut.core.annotation.Experimental;
import org.graalvm.polyglot.Value;

import java.util.function.Function;

/**
 * A Python value evaluated and cached independently in each pooled GraalPy context.
 * <p>
 * The wrapper borrows a context for each operation and resolves the context-local value before
 * invoking the supplied function. Prefer typed methods such as {@link #executeAsString(Object...)}
 * when the result is consumed immediately, because the underlying context is returned to the pool
 * before the method returns.
 *
 * @since 5.1.0
 */
@Experimental
public interface PooledValue {

    /**
     * Execute a callback with the context-local value.
     *
     * @param callback The callback receiving the value
     * @param <T> The callback result type
     * @return The callback result
     */
    <T> T withValue(Function<Value, T> callback);

    /**
     * Execute the context-local value.
     *
     * @param args Arguments to pass to the Python value
     * @return The polyglot result
     */
    default Value execute(Object... args) {
        return withValue(value -> value.execute(GraalPyRuntimeUtil.coerceArgumentsToContext(value.getContext(), args)));
    }

    /**
     * Execute the context-local value and convert the result to a string before releasing the context.
     *
     * @param args Arguments to pass to the Python value
     * @return The string result
     */
    default String executeAsString(Object... args) {
        return withValue(value -> value.execute(GraalPyRuntimeUtil.coerceArgumentsToContext(value.getContext(), args)).asString());
    }

    /**
     * Execute the context-local value and convert the result to a boolean before releasing the context.
     *
     * @param args Arguments to pass to the Python value
     * @return The boolean result
     */
    default boolean executeAsBoolean(Object... args) {
        return withValue(value -> value.execute(GraalPyRuntimeUtil.coerceArgumentsToContext(value.getContext(), args)).asBoolean());
    }
}
