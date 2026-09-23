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
import io.micronaut.http.MutableHttpResponse;
import org.jspecify.annotations.Nullable;

/**
 * A filter of one route's responses that can replace the response, declared with
 * {@link RouteFilterSpec#afterReplacing(ReplacingRouteResponseFilter)}: the {@link RouteResponseFilter}
 * that returns the response to continue with, like a {@code @ResponseFilter} method returning a
 * response. It runs where a {@link RouteResponseFilter} runs.
 *
 * <p>It can change the response it is given in place, like a {@link RouteResponseFilter}, or return
 * another response, e.g. with another status, headers or body: the response filters after it, of
 * the outer groups and of the server filters, and the client see that response. A response that is
 * not mutable is converted to a {@link MutableHttpResponse}, like the response of a route, so the
 * response filters after it can change it. It returns {@code null} to continue with the response it
 * was given, with what it changed in it.</p>
 *
 * <pre>{@code
 * routes.GET("/reports/{id}", handler).afterReplacing((request, response) ->
 *     response.code() == 404 ? HttpResponse.ok(Reports.EMPTY) : null);
 * }</pre>
 *
 * <p>It is a separate interface, declared with separate methods, so that a
 * {@link RouteResponseFilter} lambda, e.g. {@code (request, response) -> response.header("X-A", "a")},
 * is never ambiguous.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface ReplacingRouteResponseFilter {

    /**
     * Filter the response.
     *
     * @param request  The request
     * @param response The response of the route, to change in place
     * @return A response to continue with instead of the response, or {@code null} to continue with
     * the response
     * @throws Exception An error, handled by the error routes
     */
    @Nullable HttpResponse<?> filter(HttpRequest<?> request, MutableHttpResponse<?> response) throws Exception;
}
