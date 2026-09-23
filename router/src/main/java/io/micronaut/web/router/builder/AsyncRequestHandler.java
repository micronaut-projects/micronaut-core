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
     * @return The response, completed later
     * @throws Exception An error, handled by the error routes like a controller error
     */
    CompletionStage<? extends HttpResponse<?>> handle(AsyncServerHttpRequest<?> request, PathVariables pathVariables) throws Exception;
}
