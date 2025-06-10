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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;

import java.util.function.Function;

/**
 * Base interface for different filter types. Note that while the base interface is exposed, so you
 * can pass around instances of these filters, the different implementations are internal only.
 *
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Internal
sealed interface InternalHttpFilter extends GenericHttpFilter, Ordered permits AroundLegacyFilter, FilterRunner.RouteMatchResolverHttpFilter, MethodFilter {

    /**
     * Checks the filter is implementing {@link ConditionalFilter} and is enabled.
     * @param request The request
     * @return true if enabled
     */
    default boolean isEnabled(HttpRequest<?> request) {
        return true;
    }

    /**
     * If the filter supports filtering a request.
     *
     * @return true if filters request
     * @since 4.2.0
     */
    default boolean isFiltersRequest() {
        return false;
    }

    /**
     * If the filter supports filtering a response.
     *
     * @return true if filters request
     * @since 4.2.0
     */
    default boolean isFiltersResponse() {
        return false;
    }

    /**
     * If the filter with continuation.
     *
     * @return true if the filter has continuation
     * @since 4.3.0
     */
    default boolean hasContinuation() {
        return false;
    }

    /**
     * Filter request.
     *
     * @param context The filter context
     * @return The filter execution flow
     * @since 4.3.0
     */
    @NonNull
    default ExecutionFlow<FilterContext> processRequestFilter(@NonNull FilterContext context) {
        if (!isFiltersRequest()) {
            throw new IllegalStateException("Filtering request is not supported!");
        }
        return ExecutionFlow.just(context);
    }

    /**
     * Filter request.
     *
     * @param context    The filter context
     * @param downstream The downstream
     * @return The filter execution flow
     */
    @NonNull
    default ExecutionFlow<FilterContext> processRequestFilter(@NonNull FilterContext context,
                                                              @NonNull Function<FilterContext, ExecutionFlow<FilterContext>> downstream) {
        if (!isFiltersRequest()) {
            throw new IllegalStateException("Filtering request is not supported!");
        }
        return downstream.apply(context);
    }

    /**
     * Filter response.
     *
     * @param context           The filter context
     * @param exceptionToFilter The exception to filter
     * @return The filter execution flow
     */
    @NonNull
    default ExecutionFlow<FilterContext> processResponseFilter(@NonNull FilterContext context,
                                                               @Nullable Throwable exceptionToFilter) {
        if (!isFiltersResponse()) {
            throw new IllegalStateException("Filtering response is not supported!");
        }
        return ExecutionFlow.just(context);
    }

}
