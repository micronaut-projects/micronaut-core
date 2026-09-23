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
import io.micronaut.http.AsyncServerHttpRequest;
import io.micronaut.http.HttpResponse;

import java.util.concurrent.CompletionStage;

/**
 * A route handler that completes the response later. The executor is selected like for a
 * controller method returning a {@link CompletionStage}: with automatic thread selection it runs
 * on the event loop and must not block.
 *
 * <p>The handler receives no decoded body: it reads the body of the request itself, once, with
 * the methods of {@link AsyncServerHttpRequest}, which complete later or read one element or part
 * at a time. What it left open, e.g. the parts of a form it did not read, is released when the
 * returned stage completes, so the stage must include the reading of the body:</p>
 * <pre>{@code
 * routes.asyncPOST("/people", (request, pathVariables) -> request.body(Person.class)
 *     .thenApply(person -> HttpResponse.created(people.save(person))));
 * routes.asyncPOST("/upload", (request, pathVariables) -> request.parts()
 *     .part("file", part -> part.file().transferTo(uploads.resolve(UUID.randomUUID().toString())))
 *     .thenApply(found -> found ? HttpResponse.ok() : HttpResponse.badRequest()));
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
 * other threads. The reads of the body are no exception, like the {@code CompletableFuture} or
 * {@code Publisher} body of a controller method: a read, or a consumer of {@code parts()} or
 * {@code elements()}, that completes once the rest of the body arrives, completes on the thread
 * that delivers the body, without the context. A handler that needs the context once it has the
 * body continues with a propagating executor, or is a body or form handler, which is called once
 * the body arrived, like a controller method with a {@code @Body} argument.</p>
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
     * @param request       The request, whose body the handler reads
     * @param pathVariables The path variables of the matched route
     * @return The response, completed later, not {@code null}: a stage completed with {@code null}
     * is answered like the {@code null} result of a controller method, with {@code 404}, or with
     * {@code 204} if {@code micronaut.server.not-found-on-missing-body} is {@code false}
     * @throws Exception An error, handled by the error routes like a controller error
     */
    CompletionStage<? extends HttpResponse<?>> handle(AsyncServerHttpRequest<?> request, PathVariables pathVariables) throws Exception;
}
