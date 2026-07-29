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
import org.graalvm.polyglot.Context;

import java.util.function.Function;

/**
 * Executes a callback with exclusive access to a Python-owned polyglot context.
 *
 * <p>The context, and any context-owned {@code Value}, is valid only for the duration of the
 * callback and must not be retained by the callback or its result.</p>
 *
 * @author Micronaut Team
 * @since 5.2.0
 */
@Experimental
@FunctionalInterface
public interface PythonContextExecutor {

    /**
     * Execute a callback using an exclusively owned Python context.
     *
     * @param callback The callback
     * @param <T> The callback result type
     * @return The callback result
     */
    <T> T withContext(Function<Context, T> callback);
}
