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
import io.micronaut.core.execution.CompletableFutureExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * A filter declared on one route, as a function: it filters the request before the route runs
 * and may answer it instead, or it filters the response of the route. A synchronous filter runs
 * on the thread of the filter chain, or on its executor if it has one; an asynchronous filter
 * completes the filter chain when its {@link CompletionStage} completes.
 *
 * <p>Like a filter method, the filter runs with the propagated context of the filter chain in
 * scope, and it can change that context with a {@link MutablePropagatedContext}, for what runs
 * after it: the next filters, the route, the error routes and the response filters. The change of
 * a synchronous filter is taken when it returns, the change of an asynchronous filter when its
 * stage completes, e.g. an element the filter added once it looked something up.</p>
 *
 * @param requestStep  The request filter
 * @param responseStep The response filter
 * @param executor     The executor to run the filter on, or {@code null} to run it on the thread of the filter chain
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record RouteFunctionFilter(
    @Nullable RequestStep requestStep,
    @Nullable ResponseStep responseStep,
    @Nullable Supplier<? extends Executor> executor
) implements InternalHttpFilter {

    /**
     * A synchronous request filter.
     *
     * @param filter   Returns a response to answer the request with, or {@code null} to proceed
     * @param executor The executor to run the filter on, or {@code null}
     * @return The filter
     */
    static RouteFunctionFilter request(RouteFilterFunctions.Request filter, @Nullable Supplier<? extends Executor> executor) {
        return new RouteFunctionFilter(context -> {
            MutablePropagatedContext propagatedContext = MutablePropagatedContext.of(context.propagatedContext());
            HttpResponse<?> response = filter.filter(context.request(), propagatedContext);
            FilterContext next = withChangedContext(context, propagatedContext);
            return ExecutionFlow.just(response == null ? next : next.withResponse(response));
        }, null, executor);
    }

    /**
     * An asynchronous request filter.
     *
     * @param filter Completes with a response to answer the request with, or {@code null} to proceed
     * @return The filter
     */
    static RouteFunctionFilter requestAsync(RouteFilterFunctions.AsyncRequest filter) {
        return new RouteFunctionFilter(context -> {
            MutablePropagatedContext propagatedContext = MutablePropagatedContext.of(context.propagatedContext());
            return CompletableFutureExecutionFlow.just(
                filter.filter(context.request(), propagatedContext).thenApply(response -> {
                    FilterContext next = withChangedContext(context, propagatedContext);
                    return response == null ? next : next.withResponse(response);
                })
            );
        }, null, null);
    }

    /**
     * A synchronous response filter.
     *
     * @param filter   The filter
     * @param executor The executor to run the filter on, or {@code null}
     * @return The filter
     */
    static RouteFunctionFilter response(RouteFilterFunctions.Response filter, @Nullable Supplier<? extends Executor> executor) {
        return new RouteFunctionFilter(null, (context, response) -> {
            MutablePropagatedContext propagatedContext = MutablePropagatedContext.of(context.propagatedContext());
            filter.filter(context.request(), response, propagatedContext);
            return ExecutionFlow.just(withChangedContext(context, propagatedContext).withResponse(response));
        }, executor);
    }

    /**
     * An asynchronous response filter.
     *
     * @param filter Completes when the response is filtered
     * @return The filter
     */
    static RouteFunctionFilter responseAsync(RouteFilterFunctions.AsyncResponse filter) {
        return new RouteFunctionFilter(null, (context, response) -> {
            MutablePropagatedContext propagatedContext = MutablePropagatedContext.of(context.propagatedContext());
            return CompletableFutureExecutionFlow.just(
                filter.filter(context.request(), response, propagatedContext)
                    .thenApply(ignored -> withChangedContext(context, propagatedContext).withResponse(response))
            );
        }, null);
    }

    /**
     * The context of the filter chain after a filter, with the propagated context the filter
     * changed, like a filter method with a {@link MutablePropagatedContext} parameter.
     *
     * @param context           The context the filter ran with
     * @param propagatedContext The propagated context the filter was given
     * @return The context
     */
    private static FilterContext withChangedContext(FilterContext context, MutablePropagatedContext propagatedContext) {
        PropagatedContext changed = propagatedContext.getContext();
        return changed == null ? context : context.withPropagatedContext(changed);
    }

    @Override
    public boolean isFiltersRequest() {
        return requestStep != null;
    }

    @Override
    public boolean isFiltersResponse() {
        return responseStep != null;
    }

    @Override
    public ExecutionFlow<FilterContext> processRequestFilter(FilterContext context) {
        RequestStep step = requestStep;
        if (step == null) {
            return ExecutionFlow.just(context);
        }
        return run(context.propagatedContext(), () -> step.apply(context));
    }

    @Override
    public ExecutionFlow<FilterContext> processResponseFilter(FilterContext context, @Nullable Throwable exceptionToFilter) {
        ResponseStep step = responseStep;
        HttpResponse<?> response = context.response();
        if (step == null || exceptionToFilter != null || response == null) {
            return ExecutionFlow.just(context);
        }
        MutableHttpResponse<?> mutableResponse = response instanceof MutableHttpResponse<?> mutable ? mutable : response.toMutableResponse();
        return run(context.propagatedContext(), () -> step.apply(context, mutableResponse));
    }

    private ExecutionFlow<FilterContext> run(PropagatedContext propagatedContext, Step step) {
        Supplier<? extends Executor> executorSupplier = executor;
        if (executorSupplier == null) {
            return invoke(propagatedContext, step);
        }
        try {
            // like a filter method annotated @ExecuteOn, which the propagated context follows
            return ExecutionFlow.async(executorSupplier.get(), () -> invoke(propagatedContext, step));
        } catch (Throwable e) {
            return ExecutionFlow.error(e);
        }
    }

    /**
     * Run the filter with the propagated context of the filter chain in scope, like a filter
     * method: what it logs, and the tasks it submits to a propagating executor, see the context
     * the filters before it produced.
     *
     * @param propagatedContext The propagated context of the filter chain
     * @param step              The filter
     * @return The result of the filter
     */
    private static ExecutionFlow<FilterContext> invoke(PropagatedContext propagatedContext, Step step) {
        if (propagatedContext.isBound()) {
            return invoke(step);
        }
        try {
            return propagatedContext.propagate(() -> invoke(step));
        } catch (Throwable e) {
            return ExecutionFlow.error(e);
        }
    }

    private static ExecutionFlow<FilterContext> invoke(Step step) {
        try {
            return step.apply();
        } catch (Throwable e) {
            return ExecutionFlow.error(e);
        }
    }

    @Override
    public int getOrder() {
        // route filters are not sorted: they run after the application's filters, in the order declared
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * Filters the request.
     */
    @FunctionalInterface
    interface RequestStep {
        ExecutionFlow<FilterContext> apply(FilterContext context) throws Throwable;
    }

    /**
     * Filters the response.
     */
    @FunctionalInterface
    interface ResponseStep {
        ExecutionFlow<FilterContext> apply(FilterContext context, MutableHttpResponse<?> response) throws Throwable;
    }

    /**
     * A step to run.
     */
    @FunctionalInterface
    private interface Step {
        ExecutionFlow<FilterContext> apply() throws Throwable;
    }
}
