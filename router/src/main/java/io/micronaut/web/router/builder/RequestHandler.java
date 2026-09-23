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
 * A route handler written as a function of the request and the {@link PathVariables} of the
 * matched route. It runs like a controller method that returns an {@link HttpResponse}: filters, error routes and
 * body writers apply unchanged, and the executor is selected like for a blocking controller
 * method (see {@code micronaut.server.thread-selection}, {@link io.micronaut.web.router.builder.HttpRouteSpec#executeOn(String)} and
 * {@link io.micronaut.web.router.builder.HttpRouteSpec#nonBlocking()}). On whatever thread, it
 * runs with the propagated context of the request in scope, e.g. the MDC context a filter added,
 * like a controller method.
 *
 * <pre>{@code
 * routes.GET("/hello/{name}", (request, pathVariables) -> HttpResponse.ok("Hello " + pathVariables.getString("name")));
 * routes.GET("/report", (request, pathVariables) -> HttpResponse.ok(reports.build())).executeOn(TaskExecutors.BLOCKING);
 * }</pre>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 * @see io.micronaut.web.router.builder.HttpRouteBuilder#handle(io.micronaut.http.HttpMethod, String, RequestHandler)
 */
@Experimental
@FunctionalInterface
public interface RequestHandler {

    /**
     * Handle the request.
     *
     * @param request       The request
     * @param pathVariables The path variables of the matched route
     * @return The response
     * @throws Exception An error, handled by the error routes like a controller error
     */
    HttpResponse<?> handle(HttpRequest<?> request, PathVariables pathVariables) throws Exception;

}
