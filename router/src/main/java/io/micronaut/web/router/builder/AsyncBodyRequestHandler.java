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
import io.micronaut.http.body.AsyncRequestBody;

import java.util.concurrent.CompletionStage;

/**
 * A route handler that reads the body of the request itself and completes the response later:
 * the asynchronous {@link BodyRequestHandler}. Instead of the decoded body, it receives the body
 * as an {@link AsyncRequestBody}, which it reads once, with methods that complete later or read
 * one element or part at a time. The executor is selected like for an {@link AsyncRequestHandler}.
 *
 * <p>No binder decodes the body for the handler, and nothing is read before the handler asks
 * for it. What the read left open, e.g. the parts of a form the handler did not read, is released
 * when the returned stage completes, so the stage must include the reading of the body:</p>
 * <pre>{@code
 * routes.asyncPOST("/people", (request, pathVariables, body) -> body.body(Person.class)
 *     .thenApply(person -> HttpResponse.created(people.save(person))));
 * routes.asyncPOST("/upload", (request, pathVariables, body) -> body.parts()
 *     .part("file", part -> part.file().transferTo(uploads.resolve(UUID.randomUUID().toString())))
 *     .thenApply(found -> found ? HttpResponse.ok() : HttpResponse.badRequest()));
 * }</pre>
 *
 * <p>The handler runs with the propagated context of the request in scope, like an
 * {@link AsyncRequestHandler}. The reads of the body are no exception to the rule for the
 * continuations of a stage completed by another thread, like the {@code CompletableFuture} or
 * {@code Publisher} body of a controller method: a read, or a consumer of
 * {@link AsyncRequestBody#parts() parts()} or {@link AsyncRequestBody#elements(Class) elements()},
 * that completes once the rest of the body arrives, completes on the thread that delivers the
 * body, without the context. A handler that needs the context once it has the body continues with
 * a propagating executor, or is a {@link BodyRequestHandler} or a {@link FormRequestHandler}, which
 * is called once the body arrived, like a controller method with a {@code @Body} argument.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 * @see io.micronaut.web.router.builder.HttpRouteBuilder#handleAsync(io.micronaut.http.HttpMethod, String, AsyncBodyRequestHandler)
 */
@Experimental
@FunctionalInterface
public interface AsyncBodyRequestHandler {

    /**
     * Handle the request.
     *
     * @param request       The request
     * @param pathVariables The path variables of the matched route
     * @param body          The body of the request, which the handler reads
     * @return The response, completed later, not {@code null}: a stage completed with {@code null}
     * is answered like the {@code null} result of a controller method, with {@code 404}, or with
     * {@code 204} if {@code micronaut.server.not-found-on-missing-body} is {@code false}
     * @throws Exception An error, handled by the error routes like a controller error
     */
    CompletionStage<? extends HttpResponse<?>> handle(HttpRequest<?> request, PathVariables pathVariables, AsyncRequestBody body) throws Exception;
}
