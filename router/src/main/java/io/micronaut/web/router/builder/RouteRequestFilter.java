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
import org.jspecify.annotations.Nullable;

/**
 * A filter of one route's requests, declared with {@link io.micronaut.web.router.builder.HttpRouteSpec#before(RouteRequestFilter)}.
 * Like a {@code @RequestFilter} method it runs before the route, after the application's
 * filters, and can answer the request instead of the route.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface RouteRequestFilter {

    /**
     * Filter the request.
     *
     * @param request The request
     * @return A response to answer the request with instead of the route, or {@code null} to proceed
     * @throws Exception An error, handled by the error routes
     */
    @Nullable HttpResponse<?> filter(HttpRequest<?> request) throws Exception;
}
