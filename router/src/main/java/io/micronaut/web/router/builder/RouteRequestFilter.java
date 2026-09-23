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
import io.micronaut.http.MutableHttpRequest;

/**
 * A filter of one route's requests that changes the request in place, declared with
 * {@link HttpRouteSpec#before(RouteRequestFilter)}, like a {@code @RequestFilter} method with a
 * {@link MutableHttpRequest} parameter that returns nothing: it runs before the route, after the
 * application's filters, and the request continues when it returns. A header, an attribute or the
 * URI it changes is changed for what runs after it, see {@link ReplacingRouteRequestFilter} for
 * what a change in place covers. A filter that answers the request instead of the route, or
 * continues with another request, is a {@link ReplacingRouteRequestFilter}, declared with
 * {@link HttpRouteSpec#beforeReplacing(ReplacingRouteRequestFilter)}.
 *
 * <pre>{@code
 * routes.GET("/reports/{id}", reportHandler)
 *     .before(request -> request.getHeaders().add("X-Report-Format", "summary"));
 * }</pre>
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
     * @param request The request, to change in place for what runs after the filter
     * @throws Exception An error, handled by the error routes
     */
    void filter(MutableHttpRequest<?> request) throws Exception;
}
