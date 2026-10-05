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

import org.jspecify.annotations.Nullable;

/**
 * A Python exception raised in another context, as handed to Python code of the context awaiting it.
 * <p>
 * It replaces the Python exception, which Python code of the receiving context cannot read (see
 * {@link GraalPyExceptionHandler#forContext}). Its own type tells the asyncio bridge that the failure is a Python
 * one: it is raised as a Python exception, which {@code except Exception} handles, rather than as a Java
 * exception, as a generic Java failure of an awaited Java value is.
 *
 * @since 5.2.14
 */
final class PythonCrossContextException extends RuntimeException {

    /**
     * @param message The message of the Python exception
     */
    PythonCrossContextException(@Nullable String message) {
        super(message);
    }
}
