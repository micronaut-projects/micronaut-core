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
 * A handler function for an error that completes the response later: the asynchronous
 * {@link ErrorRouteHandler}, like an {@code @Error(global = true)} method returning a
 * {@link CompletionStage}. It handles the exceptions of controller routes and handler routes
 * alike, including the exception a stage of an {@link AsyncRequestHandler} or an
 * {@link AsyncBodyRequestHandler} completes with.
 *
 * <p>Like every error route it runs on the thread that handles the error, which can be the event
 * loop, so it must not block. It receives the request, without the
 * {@link io.micronaut.http.body.AsyncRequestBody} of an asynchronous route: the error can occur
 * after the route read the body, or while it was reading it, so the body is not offered to the
 * error route to read again.</p>
 *
 * <pre>{@code
 * routes.errorAsync(QuotaExceededException.class, (request, error) -> quotas.retryAfter(request)
 *     .thenApply(retryAfter -> HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, retryAfter)));
 * }</pre>
 *
 * @param <E> The type of the exception
 * @author Denis Stepanov
 * @since 5.3.0
 * @see io.micronaut.web.router.builder.HttpRouteBuilder#errorAsync(Class, AsyncErrorRouteHandler)
 */
@Experimental
@FunctionalInterface
public interface AsyncErrorRouteHandler<E extends Throwable> {

    /**
     * Handle the error.
     *
     * @param request The request
     * @param error   The exception
     * @return The response, completed later; a stage that completes exceptionally is answered
     * with the default error response, like an exception thrown by an {@link ErrorRouteHandler}
     * @throws Exception An error, answered with the default error response
     */
    CompletionStage<? extends HttpResponse<?>> handle(HttpRequest<?> request, E error) throws Exception;
}
