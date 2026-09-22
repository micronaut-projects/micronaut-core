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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Function;

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
     * Check if the filter is enabled.
     * @param filter The filter
     * @return true if enabled
     * @since 4.2.0
     */
    /**
     * Create a filter of one route's requests.
     *
     * @param filter The filter of the route's request, returning a response to answer the request
     *               with, or {@code null} to proceed
     * @return The filter
     * @since 5.3.0
     */
    @Internal
    static GenericHttpFilter createRouteRequestFilter(Function<HttpRequest<?>, @Nullable HttpResponse<?>> filter) {
        return new RouteFunctionFilter(filter, null);
    }

    /**
     * Create a filter of one route's responses.
     *
     * @param filter The filter of the route's response
     * @return The filter
     * @since 5.3.0
     */
    @Internal
    static GenericHttpFilter createRouteResponseFilter(BiConsumer<HttpRequest<?>, MutableHttpResponse<?>> filter) {
        return new RouteFunctionFilter(null, filter);
    }

    @Internal
    static boolean isEnabled(GenericHttpFilter filter) {
        return !(filter instanceof AroundLegacyFilter aroundLegacyFilter) || aroundLegacyFilter.isEnabled();
    }

}
