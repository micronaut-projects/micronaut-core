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
import io.micronaut.http.HttpResponse;

import java.util.concurrent.CompletionStage;

/**
 * A handler function for a response status that completes the response later: the asynchronous
 * {@link StatusRouteHandler}, like an {@code @Error(status = ..., global = true)} method
 * returning a {@link CompletionStage}.
 *
 * <p>Like every status route it runs on the thread that answers the status, which can be the
 * event loop, so it must not block. It receives the request as an {@link HttpRequest}: the body
 * is not offered to a status route to read, see {@link AsyncErrorRouteHandler}.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 * @see HttpRouteBuilder#statusAsync(io.micronaut.http.HttpStatus, AsyncStatusRouteHandler)
 */
@Experimental
@FunctionalInterface
public interface AsyncStatusRouteHandler {

    /**
     * Handle the status.
     *
     * @param request The request
     * @return The response, completed later; a stage that completes exceptionally is handled
     * like an exception thrown by a {@link StatusRouteHandler}
     * @throws Exception An error, handled like an exception thrown by a {@link StatusRouteHandler}
     */
    CompletionStage<? extends HttpResponse<?>> handle(HttpRequest<?> request) throws Exception;
}
