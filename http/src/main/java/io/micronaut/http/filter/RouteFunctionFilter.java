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
import io.micronaut.http.HttpMessage;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Objects;
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
 * <p>Like a filter method with a {@link MutableHttpRequest} parameter, a request filter is given
 * the request if it is mutable, and its {@link HttpRequest#mutate() mutable view} otherwise, which
 * is a server request if the request is one, see {@link MutableServerRequest}. What runs after the
 * filter sees the headers and attributes it changed in place, and the URI it changed in place: the
 * mutable request replaces the request then, like the view of a filter method, e.g. to match the
 * route with the new URI after a pre-matching filter. Like a filter method returning a request, a
 * request filter can also continue with another request, e.g. with another method or body.</p>
 *
 * <p>Like a {@code @ResponseFilter} method, a response filter is given the response, mutable, and
 * can change it in place or return another response, which replaces it for the response filters
 * after it and the client.</p>
 *
 * @param requestStep  The request filter
 * @param responseStep The response filter
 * @param executor     The executor to run the filter on, or {@code null} to run it on the thread of the filter chain
 * @param order        The order of the filter among the server filters, when it is one, see {@link #withOrder(int)}
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record RouteFunctionFilter(
    @Nullable RequestStep requestStep,
    @Nullable ResponseStep responseStep,
    @Nullable Supplier<? extends Executor> executor,
    int order
) implements InternalHttpFilter {

    /**
     * @param requestStep  The request filter
     * @param responseStep The response filter
     * @param executor     The executor to run the filter on, or {@code null}
     */
    RouteFunctionFilter(@Nullable RequestStep requestStep, @Nullable ResponseStep responseStep, @Nullable Supplier<? extends Executor> executor) {
        // route filters are not sorted: they run after the application's filters, in the order declared
        this(requestStep, responseStep, executor, Ordered.LOWEST_PRECEDENCE);
    }

    /**
     * The filter as a server filter, sorted with the server filters by its order.
     *
     * @param order The order
     * @return The filter
     */
    RouteFunctionFilter withOrder(int order) {
        return new RouteFunctionFilter(requestStep, responseStep, executor, order);
    }

    /**
     * A synchronous request filter.
     *
     * @param filter   Returns a response to answer the request with, a request to continue with,
     *                 or {@code null} to proceed
     * @param executor The executor to run the filter on, or {@code null}
     * @return The filter
     */
    static RouteFunctionFilter request(RouteFilterFunctions.Request filter, @Nullable Supplier<? extends Executor> executor) {
        return new RouteFunctionFilter(context -> {
            MutablePropagatedContext propagatedContext = MutablePropagatedContext.of(context.propagatedContext());
            MutableHttpRequest<?> request = MutableServerRequest.of(context.request());
            URI uri = request.getUri();
            HttpMessage<?> result = filter.filter(request, propagatedContext);
            return ExecutionFlow.just(next(withChangedContext(context, propagatedContext), request, uri, result));
        }, null, executor);
    }

    /**
     * An asynchronous request filter.
     *
     * @param filter Completes with a response to answer the request with, a request to continue
     *               with, or {@code null} to proceed
     * @return The filter
     */
    static RouteFunctionFilter requestAsync(RouteFilterFunctions.AsyncRequest filter) {
        return new RouteFunctionFilter(context -> {
            MutablePropagatedContext propagatedContext = MutablePropagatedContext.of(context.propagatedContext());
            MutableHttpRequest<?> request = MutableServerRequest.of(context.request());
            URI uri = request.getUri();
            CompletionStage<? extends @Nullable HttpMessage<?>> stage = Objects.requireNonNull(filter.filter(request, propagatedContext),
                "The asynchronous request filter returned no stage");
            return CompletableFutureExecutionFlow.just(
                stage.thenApply(result ->
                    next(withChangedContext(context, propagatedContext), request, uri, result))
            );
        }, null, null);
    }

    /**
     * The context of the filter chain after a request filter, like after a filter method: a
     * response answers the request, a request replaces it, and so does the mutable request the
     * filter was given when the filter changed its URI in place.
     *
     * @param context The context after the filter, with the propagated context it changed
     * @param request The mutable request the filter was given
     * @param uri     The URI of the mutable request before the filter
     * @param result  The result of the filter
     * @return The context
     */
    private static FilterContext next(FilterContext context, MutableHttpRequest<?> request, URI uri, @Nullable HttpMessage<?> result) {
        if (result instanceof HttpResponse<?> response) {
            return context.withResponse(response);
        }
        if (result instanceof HttpRequest<?> replacement) {
            return context.withRequest(replacement);
        }
        if (result != null) {
            throw new IllegalArgumentException("A request filter returns a response, a request or null, not: " + result);
        }
        if (request != context.request() && !request.getUri().equals(uri)) {
            return context.withRequest(request);
        }
        return context;
    }

    /**
     * A synchronous response filter.
     *
     * @param filter   Returns a response to continue with, or {@code null} to continue with the
     *                 response it was given
     * @param executor The executor to run the filter on, or {@code null}
     * @return The filter
     */
    static RouteFunctionFilter response(RouteFilterFunctions.Response filter, @Nullable Supplier<? extends Executor> executor) {
        return new RouteFunctionFilter(null, (context, response) -> {
            MutablePropagatedContext propagatedContext = MutablePropagatedContext.of(context.propagatedContext());
            HttpResponse<?> result = filter.filter(context.request(), response, propagatedContext);
            return ExecutionFlow.just(next(withChangedContext(context, propagatedContext), response, result));
        }, executor);
    }

    /**
     * An asynchronous response filter.
     *
     * @param filter Completes with a response to continue with, or {@code null} to continue with
     *               the response it was given
     * @return The filter
     */
    static RouteFunctionFilter responseAsync(RouteFilterFunctions.AsyncResponse filter) {
        return new RouteFunctionFilter(null, (context, response) -> {
            MutablePropagatedContext propagatedContext = MutablePropagatedContext.of(context.propagatedContext());
            CompletionStage<? extends @Nullable HttpResponse<?>> stage = Objects.requireNonNull(filter.filter(context.request(), response, propagatedContext),
                "The asynchronous response filter returned no stage");
            return CompletableFutureExecutionFlow.just(
                stage.thenApply(result -> next(withChangedContext(context, propagatedContext), response, result))
            );
        }, null);
    }

    /**
     * The context of the filter chain after a response filter, like after a {@code @ResponseFilter}
     * method: a response it returns replaces the response for the response filters after it and the
     * client; otherwise the response it was given continues, with what the filter changed in place.
     * The replacement is {@link HttpResponse#toMutableResponse() mutable}, like the response of a
     * route, so a filter method after it with a {@link MutableHttpResponse} parameter can change it.
     *
     * @param context  The context after the filter, with the propagated context it changed
     * @param response The mutable response the filter was given
     * @param result   The result of the filter
     * @return The context
     */
    private static FilterContext next(FilterContext context, MutableHttpResponse<?> response, @Nullable HttpResponse<?> result) {
        return context.withResponse(result == null ? response : result.toMutableResponse());
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
        return order;
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
