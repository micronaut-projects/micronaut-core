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

import io.micronaut.http.PathVariables;
import java.util.concurrent.CompletionStage;

/**
 * A route handler that completes the response later, like a controller method returning a
 * {@link CompletionStage}. The executor is selected like for such a controller method: with
 * automatic thread selection it runs on the event loop and must not block.
 *
 * <p>The handler does not read the body of the request, like a {@link RequestHandler}: a body
 * is discarded when the request ends, and a request that expects {@code 100 Continue} is never
 * sent it. A handler that reads the body is an {@link AsyncBodyRequestHandler}, which receives
 * the body as a parameter.</p>
 * <pre>{@code
 * routes.asyncGET("/people/{id}", (request, pathVariables) -> people.findAsync(pathVariables.getLong("id"))
 *     .thenApply(HttpResponse::ok));
 * }</pre>
 *
 * <p>Like a controller method, the handler runs with the propagated context of the request in
 * scope: the context the filters produced, e.g. the MDC context a filter added with a
 * {@link io.micronaut.core.propagation.MutablePropagatedContext}, and the request of
 * {@link io.micronaut.http.context.ServerRequestContext}. Like a controller method returning a
 * {@link CompletionStage}, a continuation of a stage completed by another thread sees the context
 * only if that thread has it, e.g. a task submitted to the {@code TaskExecutors.IO} or
 * {@code TaskExecutors.BLOCKING} executor, which propagates the context of the thread that
 * submits it; see {@link io.micronaut.core.propagation.PropagatedContext#wrap(Runnable)} for
 * other threads.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 * @see io.micronaut.web.router.builder.HttpRouteBuilder#handleAsync(io.micronaut.http.HttpMethod, String, AsyncRequestHandler)
 */
@Experimental
@FunctionalInterface
public interface AsyncRequestHandler {

    /**
     * Handle the request.
     *
     * @param request       The request
     * @param pathVariables The path variables of the matched route
     * @return The response, completed later, not {@code null}: a stage completed with {@code null}
     * is answered like the {@code null} result of a controller method, with {@code 404}, or with
     * {@code 204} if {@code micronaut.server.not-found-on-missing-body} is {@code false}
     * @throws Exception An error, handled by the error routes like a controller error
     */
    CompletionStage<? extends HttpResponse<?>> handle(HttpRequest<?> request, PathVariables pathVariables) throws Exception;
}
