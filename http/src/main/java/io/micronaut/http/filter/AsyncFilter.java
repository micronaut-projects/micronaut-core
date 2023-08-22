/*
 * Copyright 2017-2023 original authors
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

import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Wrapper around a filter that signifies the filter should be run asynchronously on the given
 * executor. Usually from an {@link io.micronaut.scheduling.annotation.ExecuteOn} annotation.
 *
 * @param actual   Actual filter
 * @param executor Executor to run the filter on
 * @author Jonas Konrad
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Internal
record AsyncFilter(InternalHttpFilter actual, Executor executor) implements InternalHttpFilter {

    @Override
    public boolean isFiltersRequest() {
        return actual.isFiltersRequest();
    }

    @Override
    public boolean isFiltersResponse() {
        return actual.isFiltersResponse();
    }

    @Override
    public ExecutionFlow<FilterContext> processRequestFilter(FilterContext context,
                                                             Function<FilterContext, ExecutionFlow<FilterContext>> downstream) {
        if (isFiltersRequest()) {
            return ExecutionFlow.async(executor, () -> actual.processRequestFilter(context, downstream));
        }
        return InternalHttpFilter.super.processRequestFilter(context, downstream);
    }

    @Override
    public ExecutionFlow<FilterContext> processResponseFilter(FilterContext context, Throwable exceptionToFilter) {
        if (isFiltersResponse()) {
            return ExecutionFlow.async(executor, () -> actual.processResponseFilter(context, exceptionToFilter));
        }
        return InternalHttpFilter.super.processResponseFilter(context, exceptionToFilter);
    }

    @Override
    public int getOrder() {
        return actual.getOrder();
    }
}
