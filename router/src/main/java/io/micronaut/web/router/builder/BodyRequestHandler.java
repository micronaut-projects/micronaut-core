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

/**
 * A route handler that receives the request body decoded to a type, like a controller method
 * with a {@code @Body} argument: the body is read and decoded by the message body readers
 * before the handler runs.
 *
 * @param <B> The body type
 * @author Denis Stepanov
 * @since 5.3.0
 * @see io.micronaut.web.router.builder.HttpRouteBuilder#handle(io.micronaut.http.HttpMethod, String, io.micronaut.core.type.Argument, BodyRequestHandler)
 */
@Experimental
@FunctionalInterface
public interface BodyRequestHandler<B> {

    /**
     * Handle the request.
     *
     * @param request       The request
     * @param pathVariables The path variables of the matched route
     * @param body          The decoded body
     * @return The response; {@code null} is answered like the {@code null} result of a controller
     * method: with {@code 404}, or with {@code 204} if {@code micronaut.server.not-found-on-missing-body}
     * is {@code false}
     * @throws Exception An error, handled by the error routes like a controller error
     */
    HttpResponse<?> handle(HttpRequest<?> request, PathVariables pathVariables, B body) throws Exception;
}
