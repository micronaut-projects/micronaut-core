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
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;

import java.util.function.Function;

/**
 * Last item in a filter chain, called when all other filters are done. Basically, this runs
 * the actual request.
 *
 * @author Jonas Konrad
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Internal
final class TerminalFilter implements InternalHttpFilter {

    private final Function<HttpRequest<?>, ExecutionFlow<MutableHttpResponse<?>>> fn;

    TerminalFilter(Function<HttpRequest<?>, ExecutionFlow<MutableHttpResponse<?>>> fn) {
        this.fn = fn;
    }

    @Override
    public boolean isFiltersRequest() {
        return true;
    }

    @Override
    public boolean isFiltersResponse() {
        return false;
    }

    @Override
    public ExecutionFlow<FilterContext> processRequestFilter(FilterContext context,
                                                             Function<FilterContext, ExecutionFlow<FilterContext>> downstream) {
        try {
            try (PropagatedContext.Scope ignore = context.propagatedContext().propagate()) {
                return fn.apply(context.request()).map(context::withResponse).flatMap(downstream);
            }
        } catch (Throwable e) {
            return ExecutionFlow.error(e);
        }
    }
}
