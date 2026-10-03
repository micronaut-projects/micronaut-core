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
import io.micronaut.http.body.AsyncRequestBody;

import java.util.concurrent.CompletionStage;

/**
 * A route handler that receives the body of the request and completes the response later: the
 * asynchronous {@link BodyRequestHandler}. The executor is selected like for an
 * {@link AsyncRequestHandler}. The route declares what the handler receives with a body stage of
 * {@link HttpRouteSpec}:
 *
 * <ul>
 *     <li>{@link HttpRouteSpec#body(io.micronaut.core.type.Argument)}: the body decoded to a
 *     type, read before the handler runs, like the {@code @Body} argument of a controller method
 *     returning a {@link CompletionStage};</li>
 *     <li>{@link HttpRouteSpec#form()}: the whole submitted form, as {@link io.micronaut.http.form.FormData};</li>
 *     <li>{@link HttpRouteSpec#body()}: the body as an {@link AsyncRequestBody}, which the handler
 *     reads once, with methods that complete later or read one element or part at a time.</li>
 * </ul>
 *
 * <p>An {@link AsyncRequestBody} is not read by a binder: nothing is read before the handler asks
 * for it. What the read left open, e.g. the parts of a form the handler did not read, is released
 * when the returned stage completes, so the stage must include the reading of the body:</p>
 * <pre>{@code
 * routes.POST("/people").body().handleAsync((request, pathVariables, body) -> body.body(Person.class)
 *     .thenApply(person -> HttpResponse.created(people.save(person))));
 * routes.POST("/upload").body().handleAsync((request, pathVariables, body) -> body.parts()
 *     .part("file", part -> part.file().transferTo(uploads.resolve(UUID.randomUUID().toString())))
 *     .thenApply(found -> found ? HttpResponse.ok() : HttpResponse.badRequest()));
 * routes.POST("/people/async").body(Person.class).handleAsync((request, pathVariables, person) ->
 *     people.saveAsync(person).thenApply(HttpResponse::created));
 * }</pre>
 *
 * <p>The handler runs with the propagated context of the request in scope, like an
 * {@link AsyncRequestHandler}. The reads of an {@link AsyncRequestBody} are no exception to the
 * rule for the continuations of a stage completed by another thread, like the
 * {@code CompletableFuture} or {@code Publisher} body of a controller method: a read, or a
 * consumer of {@link AsyncRequestBody#parts() parts()} or
 * {@link AsyncRequestBody#elements(Class) elements()}, that completes once the rest of the body
 * arrives, completes on the thread that delivers the body, without the context. A handler that
 * needs the context once it has the body continues with a propagating executor, or receives the
 * decoded body or the form, which is read before the handler is called, like a controller method
 * with a {@code @Body} argument.</p>
 *
 * @param <B> The type of the body the handler receives
 * @author Denis Stepanov
 * @since 5.3.0
 * @see HttpBodyRouteSpec#handleAsync(AsyncBodyRequestHandler)
 */
@Experimental
@FunctionalInterface
public interface AsyncBodyRequestHandler<B> {

    /**
     * Handle the request.
     *
     * @param request       The request
     * @param pathVariables The path variables of the matched route
     * @param body          The body of the request: decoded, the form, or the {@link AsyncRequestBody} the handler reads
     * @return The response, completed later, not {@code null}: a stage completed with {@code null}
     * is answered like the {@code null} result of a controller method, with {@code 404}, or with
     * {@code 204} if {@code micronaut.server.not-found-on-missing-body} is {@code false}
     * @throws Exception An error, handled by the error routes like a controller error
     */
    CompletionStage<? extends HttpResponse<?>> handle(HttpRequest<?> request, PathVariables pathVariables, B body) throws Exception;
}
