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
import io.micronaut.core.async.propagation.ReactivePropagation;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.util.Toggleable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import org.reactivestreams.Publisher;

import java.util.function.Function;

/**
 * "Legacy" filter, i.e. filter bean that implements {@link HttpFilter}.
 *
 * @param bean  The filter bean
 * @param order The filter order
 *
 * @author Jonas Konrad
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Internal
record AroundLegacyFilter(HttpFilter bean, FilterOrder order) implements InternalHttpFilter, Toggleable {

    @Override
    public boolean isEnabled(HttpRequest<?> request) {
        if (bean instanceof ConditionalFilter conditionalFilter) {
            return conditionalFilter.isEnabled(request);
        }
        return true;
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
    public boolean hasContinuation() {
        return true;
    }

    @Override
    public ExecutionFlow<FilterContext> processRequestFilter(FilterContext context,
                                                             Function<FilterContext, ExecutionFlow<FilterContext>> downstream) {
        // Legacy `Publisher<HttpResponse> proceed(..)` filters are always suspended
        FilterChainImpl chainSuspensionPoint = new FilterChainImpl(downstream, context);
        try (PropagatedContext.Scope ignore = context.propagatedContext().propagate()) {
            return chainSuspensionPoint.processResult(
                bean().doFilter(context.request(), chainSuspensionPoint),
                context.propagatedContext()
            );
        } catch (Exception e) {
            return ExecutionFlow.error(e);
        }
    }

    @Override
    public boolean isEnabled() {
        return !(bean instanceof Toggleable t) || t.isEnabled();
    }

    @Override
    public int getOrder() {
        return order.getOrder(bean);
    }

    /**
     * A filter chain implementation that triggers the downstream on the proceed invocation.
     */
    private static final class FilterChainImpl implements ClientFilterChain, ServerFilterChain {

        private final Function<FilterContext, ExecutionFlow<FilterContext>> downstream;
        private FilterContext filterContext;

        private FilterChainImpl(Function<FilterContext, ExecutionFlow<FilterContext>> downstream,
                                FilterContext filterContext) {
            this.downstream = downstream;
            this.filterContext = filterContext;
        }

        @Override
        public Publisher<? extends HttpResponse<?>> proceed(MutableHttpRequest<?> request) {
            filterContext = filterContext.withRequest(request).withPropagatedContext(PropagatedContext.find().orElse(filterContext.propagatedContext()));
            return ReactiveExecutionFlow.fromFlow(
                downstream.apply(filterContext).<HttpResponse<?>>map(newFilterContext -> {
                    filterContext = newFilterContext;
                    return newFilterContext.response();
                })
            ).toPublisher();
        }

        @Override
        public Publisher<MutableHttpResponse<?>> proceed(HttpRequest<?> request) {
            filterContext = filterContext.withRequest(request).withPropagatedContext(PropagatedContext.find().orElse(filterContext.propagatedContext()));
            return ReactiveExecutionFlow.fromFlow(
                downstream.apply(filterContext).<MutableHttpResponse<?>>map(newFilterContext -> {
                    filterContext = newFilterContext;
                    return (MutableHttpResponse<?>) newFilterContext.response();
                })
            ).toPublisher();
        }

        private ExecutionFlow<FilterContext> processResult(Publisher<? extends HttpResponse<?>> publisher, PropagatedContext propagatedContext) {
            return ReactiveExecutionFlow.fromPublisher(ReactivePropagation.propagate(propagatedContext, publisher))
                .map(httpResponse -> filterContext.withResponse(httpResponse));
        }

    }
}
