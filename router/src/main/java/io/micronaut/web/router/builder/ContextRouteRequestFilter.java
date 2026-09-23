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
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import org.jspecify.annotations.Nullable;

/**
 * A filter of one route's requests that changes the propagated context, declared with
 * {@link HttpRouteSpec#before(ContextRouteRequestFilter)}: the {@link RouteRequestFilter} that
 * receives a {@link MutablePropagatedContext}, like a {@code @RequestFilter} method with a
 * {@code MutablePropagatedContext} parameter. An element it adds, e.g. an MDC context, a tracing
 * span or a security context, is in scope for what runs after it: the next filters, the route
 * handler, synchronous or not, the error and status routes, and the response filters.
 *
 * <pre>{@code
 * routes.GET("/orders", handler).before((request, propagatedContext) -> {
 *     propagatedContext.add(new MdcPropagationContext(Map.of("trackingId", request.getHeaders().get("X-TrackingId"))));
 *     return null;
 * });
 * }</pre>
 *
 * <p>Like every route filter it runs with the propagated context of the filter chain in scope.
 * The change is taken when it returns: the element is not in scope while the filter itself runs.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface ContextRouteRequestFilter {

    /**
     * Filter the request.
     *
     * @param request           The request
     * @param propagatedContext The propagated context, to change for what runs after the filter
     * @return A response to answer the request with instead of the route, or {@code null} to proceed
     * @throws Exception An error, handled by the error routes
     */
    @Nullable HttpResponse<?> filter(HttpRequest<?> request, MutablePropagatedContext propagatedContext) throws Exception;
}
