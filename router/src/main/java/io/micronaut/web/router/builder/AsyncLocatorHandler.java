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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpRequest;

import java.util.concurrent.CompletionStage;

/**
 * Locates the target of the rest of a path later, for
 * {@link HttpRouteBuilder#locateAsync(String, AsyncLocatorHandler, java.util.function.Function)}:
 * the asynchronous form of {@link LocatorHandler}, e.g. to load the target from a database
 * without blocking the thread that matches the request.
 *
 * <pre>{@code
 * routes.locateAsync("/orders/{id}",
 *     (request, pathVariables) -> orders.findAsync(pathVariables.getLong("id")),
 *     order -> itemRoutes);
 * }</pre>
 *
 * <p>The router calls the locator while it matches the request and does not wait for the
 * stage: the request is matched with the routes of the table of the target when the stage
 * completes, on the thread that completes it, with the propagated context of the request. The
 * target is located once per request: the router reuses it, e.g. to find the methods allowed for
 * the path when no route of the table matched. A stage that completes with {@code null} answers
 * the request with {@code 404}; a stage that fails, or a locator that throws, is answered by the
 * error routes, like a failed controller method.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface AsyncLocatorHandler {

    /**
     * Locate the target.
     *
     * @param request       The request
     * @param pathVariables The path variables of the prefix, and of the prefixes of the locators
     *                      that located this one; {@link PathVariables#locatedTarget()} is the
     *                      target that owns this locator, if any
     * @return The stage of the target, which completes with {@code null} if there is none: the
     * request is not found
     * @throws Exception An error, handled by the error routes like a controller error
     */
    CompletionStage<?> locate(HttpRequest<?> request, PathVariables pathVariables) throws Exception;
}
