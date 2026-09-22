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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;

/**
 * A handler function for an error: it answers a request whose handling failed with an exception
 * of the type it is registered for, like an {@code @Error(global = true)} method. It handles the
 * exceptions of controller routes and handler routes alike.
 *
 * @param <E> The type of the exception
 * @author Denis Stepanov
 * @since 5.3.0
 * @see io.micronaut.web.router.builder.RouteBuilder#error(Class, ErrorRouteHandler)
 */
@Experimental
@FunctionalInterface
public interface ErrorRouteHandler<E extends Throwable> {

    /**
     * Handle the error.
     *
     * @param request The request
     * @param error   The exception
     * @return The response
     * @throws Exception An error, answered with the default error response
     */
    HttpResponse<?> handle(HttpRequest<?> request, E error) throws Exception;
}
