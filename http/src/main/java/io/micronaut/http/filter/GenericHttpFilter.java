/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.filter;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Base interface for different filter types. Note that while the base interface is exposed, so you
 * can pass around instances of these filters, the different implementations are internal only.
 *
 * @author Jonas Konrad
 * @since 4.0.0
 */
public sealed interface GenericHttpFilter permits InternalHttpFilter {

    /**
     * When the filter is using the continuation it needs to be suspended and wait for the response.
     * @return true if suspended
     * @deprecated Not needed anymore
     */
    @Deprecated(forRemoval = true)
    default boolean isSuspended() {
        return false;
    }

    /**
     * @return true if the filter can receive the processing exception.
     * @deprecated Not needed anymore
     */
    @Deprecated(forRemoval = true)
    default boolean isFiltersException() {
        return false;
    }

    /**
     * Create a legacy filter.
     * @param bean The {@link HttpFilter} bean.
     * @param order The order
     * @return new filter
     * @since 4.2.0
     */
    @Internal
    static GenericHttpFilter createLegacyFilter(HttpFilter bean, FilterOrder order) {
        return new AroundLegacyFilter(bean, order);
    }

    /**
     * Create a filter of one route's requests.
     *
     * @param filter   Returns a response to answer the request with instead of the route, or {@code null} to proceed
     * @param executor The executor to run the filter on, or {@code null} to run it on the thread of the filter chain
     * @return The filter
     * @since 5.3.0
     */
    @Internal
    static GenericHttpFilter createRouteRequestFilter(RouteFilterFunctions.Request filter,
                                                      @Nullable Supplier<? extends Executor> executor) {
        return RouteFunctionFilter.request(filter, executor);
    }

    /**
     * Create an asynchronous filter of one route's requests.
     *
     * @param filter Completes with a response to answer the request with instead of the route, or {@code null} to proceed
     * @return The filter
     * @since 5.3.0
     */
    @Internal
    static GenericHttpFilter createAsyncRouteRequestFilter(RouteFilterFunctions.AsyncRequest filter) {
        return RouteFunctionFilter.requestAsync(filter);
    }

    /**
     * Create a filter of one route's responses.
     *
     * @param filter   The filter of the route's response
     * @param executor The executor to run the filter on, or {@code null} to run it on the thread of the filter chain
     * @return The filter
     * @since 5.3.0
     */
    @Internal
    static GenericHttpFilter createRouteResponseFilter(RouteFilterFunctions.Response filter,
                                                       @Nullable Supplier<? extends Executor> executor) {
        return RouteFunctionFilter.response(filter, executor);
    }

    /**
     * Create an asynchronous filter of one route's responses.
     *
     * @param filter Completes when the route's response is filtered
     * @return The filter
     * @since 5.3.0
     */
    @Internal
    static GenericHttpFilter createAsyncRouteResponseFilter(RouteFilterFunctions.AsyncResponse filter) {
        return RouteFunctionFilter.responseAsync(filter);
    }

    /**
     * A filter of one route, created with the methods above, as a server filter: sorted with the
     * server filters by the given order, like a filter method of a {@code @ServerFilter} bean.
     *
     * @param routeFilter The filter of a route
     * @param order       The order
     * @return The server filter
     * @since 5.3.0
     */
    @Internal
    static GenericHttpFilter withOrder(GenericHttpFilter routeFilter, int order) {
        if (!(routeFilter instanceof RouteFunctionFilter filter)) {
            throw new IllegalArgumentException("Not a filter of a route: " + routeFilter);
        }
        return filter.withOrder(order);
    }

    /**
     * Check if the filter is enabled.
     * @param filter The filter
     * @return true if enabled
     * @since 4.2.0
     */
    @Internal
    static boolean isEnabled(GenericHttpFilter filter) {
        return !(filter instanceof AroundLegacyFilter aroundLegacyFilter) || aroundLegacyFilter.isEnabled();
    }

}
