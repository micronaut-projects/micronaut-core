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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A filter declared on one route, as a function: it filters the request before the route runs
 * and may answer it instead, or it filters the response of the route. A synchronous filter runs
 * on the thread of the filter chain, or on its executor if it has one; an asynchronous filter
 * completes the filter chain when its {@link CompletionStage} completes.
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
    static RouteFunctionFilter request(Function<HttpRequest<?>, @Nullable HttpResponse<?>> filter, @Nullable Supplier<? extends Executor> executor) {
        return new RouteFunctionFilter(context -> {
            HttpResponse<?> response = filter.apply(context.request());
            return ExecutionFlow.just(response == null ? context : context.withResponse(response));
        }, null, executor);
    }

    /**
     * An asynchronous request filter.
     *
     * @param filter Completes with a response to answer the request with, or {@code null} to proceed
     * @return The filter
     */
    static RouteFunctionFilter requestAsync(Function<HttpRequest<?>, ? extends CompletionStage<? extends @Nullable HttpResponse<?>>> filter) {
        return new RouteFunctionFilter(context -> CompletableFutureExecutionFlow.just(
            filter.apply(context.request()).thenApply(response -> response == null ? context : context.withResponse(response))
        ), null, null);
    }

    /**
     * A synchronous response filter.
     *
     * @param filter   The filter
     * @param executor The executor to run the filter on, or {@code null}
     * @return The filter
     */
    static RouteFunctionFilter response(BiConsumer<HttpRequest<?>, MutableHttpResponse<?>> filter, @Nullable Supplier<? extends Executor> executor) {
        return new RouteFunctionFilter(null, (context, response) -> {
            filter.accept(context.request(), response);
            return ExecutionFlow.just(context.withResponse(response));
        }, executor);
    }

    /**
     * An asynchronous response filter.
     *
     * @param filter Completes when the response is filtered
     * @return The filter
     */
    static RouteFunctionFilter responseAsync(BiFunction<HttpRequest<?>, MutableHttpResponse<?>, ? extends CompletionStage<?>> filter) {
        return new RouteFunctionFilter(null, (context, response) -> CompletableFutureExecutionFlow.just(
            filter.apply(context.request(), response).thenApply(ignored -> context.withResponse(response))
        ), null);
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
        return run(() -> step.apply(context));
    }

    @Override
    public ExecutionFlow<FilterContext> processResponseFilter(FilterContext context, @Nullable Throwable exceptionToFilter) {
        ResponseStep step = responseStep;
        HttpResponse<?> response = context.response();
        if (step == null || exceptionToFilter != null || response == null) {
            return ExecutionFlow.just(context);
        }
        MutableHttpResponse<?> mutableResponse = response instanceof MutableHttpResponse<?> mutable ? mutable : response.toMutableResponse();
        return run(() -> step.apply(context, mutableResponse));
    }

    private ExecutionFlow<FilterContext> run(Step step) {
        Supplier<? extends Executor> executorSupplier = executor;
        if (executorSupplier == null) {
            return invoke(step);
        }
        try {
            // like a filter method annotated @ExecuteOn, which the propagated context follows
            return ExecutionFlow.async(executorSupplier.get(), () -> invoke(step));
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
