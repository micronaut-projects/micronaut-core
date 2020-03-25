/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.exceptions;

import io.micronaut.http.HttpRequest;

/**
 * A generic hook for handling exceptions that occurs during the execution of an HTTP request.
 *
 * @param <T> The throwable
 * @param <R> The result type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ExceptionHandler<T extends Throwable, R> {

    /**
     * Handles an exception and returns the result.
     *
     * @param request   The request
     * @param exception The exception type
     * @return The result
     */
    R handle(HttpRequest request, T exception);
}
