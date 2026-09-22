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
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A filter declared on one route, as a function: it filters the request before the route runs
 * and may answer it instead, or it filters the response of the route. It runs synchronously on
 * the thread of the filter chain.
 *
 * @param requestFilter  The request filter, returning a response to answer the request with, or {@code null} to proceed
 * @param responseFilter The response filter
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record RouteFunctionFilter(
    @Nullable Function<HttpRequest<?>, @Nullable HttpResponse<?>> requestFilter,
    @Nullable BiConsumer<HttpRequest<?>, MutableHttpResponse<?>> responseFilter
) implements InternalHttpFilter {

    @Override
    public boolean isFiltersRequest() {
        return requestFilter != null;
    }

    @Override
    public boolean isFiltersResponse() {
        return responseFilter != null;
    }

    @Override
    public ExecutionFlow<FilterContext> processRequestFilter(FilterContext context) {
        Function<HttpRequest<?>, @Nullable HttpResponse<?>> filter = requestFilter;
        if (filter == null) {
            return ExecutionFlow.just(context);
        }
        try {
            HttpResponse<?> response = filter.apply(context.request());
            return ExecutionFlow.just(response == null ? context : context.withResponse(response));
        } catch (Throwable e) {
            return ExecutionFlow.error(e);
        }
    }

    @Override
    public ExecutionFlow<FilterContext> processResponseFilter(FilterContext context, @Nullable Throwable exceptionToFilter) {
        BiConsumer<HttpRequest<?>, MutableHttpResponse<?>> filter = responseFilter;
        HttpResponse<?> response = context.response();
        if (filter == null || exceptionToFilter != null || response == null) {
            return ExecutionFlow.just(context);
        }
        try {
            MutableHttpResponse<?> mutableResponse = response instanceof MutableHttpResponse<?> mutable ? mutable : response.toMutableResponse();
            filter.accept(context.request(), mutableResponse);
            return ExecutionFlow.just(context.withResponse(mutableResponse));
        } catch (Throwable e) {
            return ExecutionFlow.error(e);
        }
    }

    @Override
    public int getOrder() {
        // route filters are not sorted: they run after the application's filters, in the order declared
        return Ordered.LOWEST_PRECEDENCE;
    }
}
