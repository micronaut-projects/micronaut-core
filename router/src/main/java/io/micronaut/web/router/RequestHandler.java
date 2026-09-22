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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;

/**
 * A route handler written as a function of the request. It runs like a controller method that
 * takes the {@link HttpRequest} and returns an {@link HttpResponse}: filters, error routes, body
 * writers and executor selection apply unchanged. As a blocking method it runs on the blocking
 * executor by default.
 *
 * <pre>{@code
 * routes.GET("/hello/{name}", request -> HttpResponse.ok("Hello " + RequestHandler.pathVariable(request, "name")));
 * }</pre>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 * @see RouteBuilder#handle(io.micronaut.http.HttpMethod, String, RequestHandler)
 */
@Experimental
@FunctionalInterface
public interface RequestHandler {

    /**
     * Handle the request.
     *
     * @param request The request
     * @return The response
     * @throws Exception An error, handled by the error routes like a controller error
     */
    HttpResponse<?> handle(HttpRequest<?> request) throws Exception;

    /**
     * A path variable of the matched route.
     *
     * @param request The request
     * @param name    The name of the variable
     * @return The value, or {@code null} if the route has no such variable or it is not present
     */
    static @org.jspecify.annotations.Nullable String pathVariable(HttpRequest<?> request, String name) {
        return io.micronaut.http.BasicHttpAttributes.getRouteMatchInfo(request)
            .map(info -> info.getVariableValues().get(name))
            .map(Object::toString)
            .orElse(null);
    }
}
