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
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.CompletableFutureExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.execution.ImperativeExecutionFlow;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.inject.ExecutableMethod;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.CorePublisher;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * @implNote Legacy filters had a strict execution flow: filter1->chain.proceed->filter2->chain.proceed...
 * This leads to deep call stacks and requires use of reactive flows. The new filter API is more
 * flexible. Filters do not have to accept a chain they call proceed on, they do not have to use
 * reactive types, and so on.<br>
 * {@link FilterRunner} takes advantage of this flexibility to optimize execution of filters. Most
 * importantly, if a filter returns an immediate value (as opposed to a reactive flow),
 * {@link FilterRunner} can execute filters <i>sequentially</i>, instead of in a recursive fashion.<br>
 * The implementation of this is inspired by kotlin coroutines. Filter execution essentially
 * happens in a loop ({@link #workRequest()} and {@link #workResponse()}), but the loop counter is
 * an instance variable ({@link #index}), and the loop can sometimes <i>suspend</i>, e.g. when a
 * filter returns a reactive flow instead of an immediate value. When suspension happens, the loop
 * exits early, and {@link #workRequest()} (or {@link #workResponse()}) will be called again on
 * unsuspend (e.g. when the reactive flow completes).
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
public class FilterRunner {
    private static final Logger LOG = LoggerFactory.getLogger(FilterRunner.class);
    private static final Predicate<FilterRunner> FILTER_CONDITION_ALWAYS_TRUE = runner -> true;

    private final ConversionService conversionService;
    private final List<GenericHttpFilter> filters;

    private HttpRequest<?> request;
    private HttpResponse<?> response;
    private Throwable failure;
    private SuspensionPoint responseSuspensionPoint = new SuspensionPoint(-1, null);
    private int index;
    private boolean responseNeedsProcessing = false;

    private Context reactorContext = Context.empty();

    /**
     * Create a new filter runner, to be used only once.
     *
     * @param conversionService The conversion service
     * @param filters The filters to run
     */
    public FilterRunner(ConversionService conversionService, List<GenericHttpFilter> filters) {
        this.conversionService = conversionService;
        this.filters = Objects.requireNonNull(filters, "filters");
    }

    private static void checkOrdered(List<GenericHttpFilter> filters) {
        if (!filters.stream().allMatch(f -> f instanceof Ordered)) {
            throw new IllegalStateException("Some filters cannot be ordered: " + filters);
        }
    }

    /**
     * Sort filters according to their declared order (e.g. annotation,
     * {@link Ordered#getOrder()}...). List must not contain terminal filters.
     *
     * @param filters The list of filters to sort in place
     */
    public static void sort(@NonNull List<GenericHttpFilter> filters) {
        checkOrdered(filters);
        OrderUtil.sort(filters);
    }

    /**
     * Sort filters according to their declared order (e.g. annotation,
     * {@link Ordered#getOrder()}...). List must not contain terminal filters. Reverse order.
     *
     * @param filters The list of filters to sort in place
     */
    public static void sortReverse(@NonNull List<GenericHttpFilter> filters) {
        checkOrdered(filters);
        OrderUtil.reverseSort(filters);
    }

    /**
     * Transform a response, e.g. by replacing an error response with an exception. Called before
     * every filter.
     *
     * @param request  The current request
     * @param response The current response
     * @return A flow that will be passed on to the next filter
     */
    protected ExecutionFlow<? extends HttpResponse<?>> processResponse(HttpRequest<?> request, HttpResponse<?> response) {
        return ExecutionFlow.just(response);
    }

    /**
     * Transform a failure, e.g. by replacing an exception with an error response. Called before
     * every filter.
     *
     * @param request  The current request
     * @param failure  The failure
     * @return A flow that will be passed on to the next filter
     */
    protected ExecutionFlow<? extends HttpResponse<?>> processFailure(HttpRequest<?> request, Throwable failure) {
        return ExecutionFlow.error(failure);
    }

    private boolean processResponse0() {
        ExecutionFlow<? extends HttpResponse<?>> flow;
        if (failure == null) {
            flow = processResponse(request, response);
        } else {
            flow = processFailure(request, failure);
        }
        ImperativeExecutionFlow<? extends HttpResponse<?>> done = flow.tryComplete();
        if (done != null) {
            failure = done.getError();
            response = done.getValue();
            return true;
        } else {
            flow.onComplete((resp, fail) -> {
                failure = fail;
                response = resp;
                workResponse();
            });
            return false;
        }
    }

    /**
     * Set the initial reactor context. This is passed on to every filter that requests a reactive
     * type, and, if applicable, to the
     * {@link io.micronaut.http.filter.GenericHttpFilter.TerminalWithReactorContext terminal}.
     *
     * @param reactorContext The reactor context, may be updated by filters
     * @return This filter runner, for chaining
     */
    public final FilterRunner reactorContext(Context reactorContext) {
        this.reactorContext = reactorContext;
        return this;
    }

    /**
     * Execute the filters for the given request. May only be called once
     *
     * @param request The request
     * @return The flow that completes after all filters and the terminal operation, with the final
     * response
     */
    public final ExecutionFlow<MutableHttpResponse<?>> run(HttpRequest<?> request) {
        if (this.request != null) {
            throw new IllegalStateException("Can only process one request");
        }
        this.request = request;

        ExecutionFlow<HttpResponse<?>> resultFlow = CompletableFutureExecutionFlow.just(responseSuspensionPoint);
        workRequest();
        //noinspection unchecked,rawtypes
        return (ExecutionFlow) resultFlow;
    }

    private void workRequest() {
        while (true) {
            if (!workRequestFilter(filters.get(index++))) {
                return;
            }
        }
    }

    private boolean workRequestFilter(GenericHttpFilter filter) {
        Executor executeOn;
        if (filter instanceof GenericHttpFilter.Async async) {
            executeOn = async.executor();
            filter = async.actual();
        } else {
            executeOn = null;
        }

        if (filter instanceof FilterMethod<?> before) {
            if (before.isResponseFilter) {
                // skip filter, only used for response
                return true;
            }
            if (executeOn == null) {
                // possibly continue with next filter
                return before.invokeBefore(this, null);
            } else {
                executeOn.execute(() -> {
                    if (before.invokeBefore(this, executeOn)) {
                        workRequest();
                    }
                });
                return false;
            }
        } else if (filter instanceof GenericHttpFilter.AroundLegacy around) {
            FilterChainImpl chainSuspensionPoint = new FilterChainImpl(index - 1, responseSuspensionPoint);
            chainSuspensionPoint.completeOn = executeOn;
            responseSuspensionPoint = chainSuspensionPoint;
            if (executeOn == null) {
                try {
                    around.bean().doFilter(request, chainSuspensionPoint).subscribe(chainSuspensionPoint);
                } catch (Throwable e) {
                    chainSuspensionPoint.forwardResponse(null, e);
                }
            } else {
                executeOn.execute(() -> {
                    try {
                        around.bean().doFilter(request, chainSuspensionPoint).subscribe(chainSuspensionPoint);
                    } catch (Throwable e) {
                        chainSuspensionPoint.forwardResponse(null, e);
                    }
                });
            }
            // suspend
            return false;
        } else if (filter instanceof GenericHttpFilter.TerminalReactive || filter instanceof GenericHttpFilter.Terminal || filter instanceof GenericHttpFilter.TerminalWithReactorContext) {
            if (executeOn != null) {
                throw new AssertionError("Async terminal filters not supported");
            }

            ExecutionFlow<? extends HttpResponse<?>> terminalFlow;
            if (filter instanceof GenericHttpFilter.TerminalWithReactorContext t) {
                try {
                    terminalFlow = t.execute(request, reactorContext);
                } catch (Throwable e) {
                    terminalFlow = ExecutionFlow.error(e);
                }
            } else if (filter instanceof GenericHttpFilter.Terminal t) {
                try {
                    terminalFlow = t.execute(request);
                } catch (Throwable e) {
                    terminalFlow = ExecutionFlow.error(e);
                }
            } else {
                terminalFlow = ReactiveExecutionFlow.fromPublisher(Mono.from(((GenericHttpFilter.TerminalReactive) filter).responsePublisher())
                    .contextWrite(reactorContext));
            }
            // this is almost never available immediately, so don't bother with asDone checks
            terminalFlow.onComplete((resp, fail) -> {
                response = resp;
                failure = fail;
                responseNeedsProcessing = true;
                index--;
                workResponse();
            });
            // request work is done
            return false;
        } else {
            throw new IllegalStateException("Unknown filter type");
        }
    }

    private void workResponse() {
        while (true) {
            if (responseNeedsProcessing) {
                responseNeedsProcessing = false;
                if (!processResponse0()) {
                    // suspend
                    return;
                }
            }

            index--;

            // if there is a suspension point for this filter, notify the suspension point instead.
            // we don't need to execute this filter again, it already got called for the request.
            if (responseSuspensionPoint.filterIndex == index) {
                SuspensionPoint suspensionPoint = responseSuspensionPoint;
                responseSuspensionPoint = suspensionPoint.next;
                boolean completed;
                if (failure == null) {
                    completed = suspensionPoint.complete(response);
                } else {
                    completed = suspensionPoint.completeExceptionally(failure);
                }
                if (!completed) {
                    if (failure == null) {
                        LOG.warn("Dropped completion");
                    } else {
                        LOG.warn("Dropped completion with exception", failure);
                    }
                }
                return;
            }
            GenericHttpFilter filter = filters.get(index);

            Executor executeOn = null;
            if (filter instanceof GenericHttpFilter.Async async) {
                executeOn = async.executor();
                filter = async.actual();
            }

            if (filter instanceof FilterMethod<?> after && after.isResponseFilter) {
                if (executeOn == null) {
                    if (!after.invokeAfter(this)) {
                        // suspend
                        return;
                    }
                } else {
                    executeOn.execute(() -> {
                        if (after.invokeAfter(this)) {
                            workResponse();
                        }
                    });
                    // suspend
                    return;
                }
            }
        }
    }

    @Internal
    public static <T> FilterMethod<T> prepareFilterMethod(T bean, ExecutableMethod<T, ?> method, boolean isResponseFilter, FilterOrder order) throws IllegalArgumentException {
        return prepareFilterMethod(bean, method, method.getArguments(), method.getReturnType().asArgument(), isResponseFilter, order);
    }

    @Internal
    public static <T> FilterMethod<T> prepareFilterMethod(T bean, ExecutableMethod<T, ?> method, Argument<?>[] arguments, Argument<?> returnType, boolean isResponseFilter, FilterOrder order) throws IllegalArgumentException {
        FilterArgBinder[] fulfilled = new FilterArgBinder[arguments.length];
        Predicate<FilterRunner> filterCondition = FILTER_CONDITION_ALWAYS_TRUE;
        boolean skipOnError = isResponseFilter;

        int passedOnContinuationArgIndex = -1;
        for (int i = 0; i < arguments.length; i++) {
            Argument<?> argument = arguments[i];
            if (argument.getType().isAssignableFrom(HttpRequest.class)) {
                fulfilled[i] = runner -> runner.request;
            } else if (argument.getType().isAssignableFrom(MutableHttpRequest.class)) {
                fulfilled[i] = runner -> {
                    if (!(runner.request instanceof MutableHttpRequest<?>)) {
                        runner.request = runner.request.mutate();
                    }
                    return runner.request;
                };
            } else if (argument.getType().isAssignableFrom(MutableHttpResponse.class)) {
                if (!isResponseFilter) {
                    throw new IllegalArgumentException("Filter is called before the response is known, can't have a response argument");
                }
                fulfilled[i] = runner -> runner.response;
            } else if (Throwable.class.isAssignableFrom(argument.getType())) {
                if (!isResponseFilter) {
                    throw new IllegalArgumentException("Request filters cannot handle exceptions");
                }
                if (!argument.isNullable()) {
                    filterCondition = filterCondition.and(runner -> runner.failure != null && argument.isInstance(runner.failure));
                    fulfilled[i] = runner -> runner.failure;
                } else {
                    fulfilled[i] = runner -> {
                        if (runner.failure != null && argument.isInstance(runner.failure)) {
                            return runner.failure;
                        } else {
                            return null;
                        }
                    };
                }
                skipOnError = false;
            } else if (argument.getType() == FilterContinuation.class) {
                if (isResponseFilter) {
                    throw new IllegalArgumentException("Response filters cannot use filter continuations");
                }
                if (passedOnContinuationArgIndex != -1) {
                    throw new IllegalArgumentException("Only one continuation per filter is allowed");
                }
                Argument<?> continuationReturnType = argument.getFirstTypeVariable().orElseThrow(() -> new IllegalArgumentException("Continuations must specify generic type"));
                if (isReactive(continuationReturnType) && continuationReturnType.getWrappedType().isAssignableFrom(MutableHttpResponse.class)) {
                    fulfilled[i] = runner -> {
                        SuspensionPoint newSuspensionPoint = runner.new ReactiveContinuationImpl<>(
                            runner.index - 1,
                            runner.responseSuspensionPoint,
                            continuationReturnType.getType()
                        );
                        // invokeBefore will detect the new suspension point and handle it accordingly
                        runner.responseSuspensionPoint = newSuspensionPoint;
                        return newSuspensionPoint;
                    };
                } else if (continuationReturnType.getType().isAssignableFrom(MutableHttpResponse.class)) {
                    fulfilled[i] = runner -> {
                        SuspensionPoint newSuspensionPoint = runner.new BlockingContinuationImpl(
                            runner.index - 1,
                            runner.responseSuspensionPoint
                        );
                        // invokeBefore will detect the new suspension point and handle it accordingly
                        runner.responseSuspensionPoint = newSuspensionPoint;
                        return newSuspensionPoint;
                    };
                } else {
                    throw new IllegalArgumentException("Unsupported continuation type: " + continuationReturnType);
                }
                passedOnContinuationArgIndex = i;

            } else {
                throw new IllegalArgumentException("Unsupported filter argument type: " + argument);
            }
        }
        if (skipOnError) {
            filterCondition = filterCondition.and(r -> r.failure == null);
        }
        if (filterCondition == FILTER_CONDITION_ALWAYS_TRUE) {
            filterCondition = null;
        }
        return new FilterMethod<>(
            order,
            bean,
            method,
            isResponseFilter,
            fulfilled,
            filterCondition,
            passedOnContinuationArgIndex,
            prepareReturnHandler(returnType, isResponseFilter, passedOnContinuationArgIndex != -1, false)
        );
    }

    private static boolean isReactive(Argument<?> continuationReturnType) {
        // Argument.isReactive doesn't work in http-validation, this is a workaround
        return continuationReturnType.isReactive() || continuationReturnType.getType() == Publisher.class;
    }

    private static FilterReturnHandler prepareReturnHandler(Argument<?> type, boolean isResponseFilter, boolean hasContinuation, boolean fromOptional) throws IllegalArgumentException {
        if (type.isOptional()) {
            FilterReturnHandler next = prepareReturnHandler(type.getWrappedType(), isResponseFilter, hasContinuation, true);
            return (r, o, c) -> next.handle(r, o == null ? null : ((Optional<?>) o).orElse(null), c);
        }
        if (type.isVoid()) {
            if (hasContinuation) {
                return FilterReturnHandler.VOID_WITH_CONTINUATION;
            } else {
                return FilterReturnHandler.VOID;
            }
        }
        boolean nullable = type.isNullable() || fromOptional;
        if (!isResponseFilter) {
            if (type.getType() == HttpRequest.class || type.getType() == MutableHttpRequest.class) {
                if (hasContinuation) {
                    throw new IllegalArgumentException("Filter method that accepts a continuation cannot return an HttpRequest");
                }
                if (nullable) {
                    return FilterReturnHandler.REQUEST_NULLABLE;
                } else {
                    return FilterReturnHandler.REQUEST;
                }
            } else if (type.getType() == HttpResponse.class || type.getType() == MutableHttpResponse.class) {
                if (hasContinuation) {
                    return FilterReturnHandler.FROM_REQUEST_RESPONSE_WITH_CONTINUATION;
                } else {
                    if (nullable) {
                        return FilterReturnHandler.FROM_REQUEST_RESPONSE_NULLABLE;
                    } else {
                        return FilterReturnHandler.FROM_REQUEST_RESPONSE;
                    }
                }
            }
        } else {
            if (hasContinuation) {
                throw new AssertionError();
            }
            if (type.getType() == HttpResponse.class || type.getType() == MutableHttpResponse.class) {
                if (nullable) {
                    return FilterReturnHandler.FROM_RESPONSE_RESPONSE_NULLABLE;
                } else {
                    return FilterReturnHandler.FROM_RESPONSE_RESPONSE;
                }
            }
        }
        if (isReactive(type)) {
            var next = prepareReturnHandler(type.getWrappedType(), isResponseFilter, hasContinuation, false);
            return new DelayedFilterReturnHandler(isResponseFilter, next, nullable) {
                @Override
                protected ExecutionFlow<?> toFlow(FilterRunner runner, Object o) {
                    //noinspection unchecked
                    return ReactiveExecutionFlow.fromPublisher(
                        Mono.from(Publishers.convertPublisher(runner.conversionService, o, Publisher.class))
                            .contextWrite(runner.reactorContext));
                }
            };
        } else if (type.isAsync()) {
            var next = prepareReturnHandler(type.getWrappedType(), isResponseFilter, hasContinuation, false);
            return new DelayedFilterReturnHandler(isResponseFilter, next, nullable) {
                @Override
                protected ExecutionFlow<?> toFlow(FilterRunner runner, Object o) {
                    //noinspection unchecked
                    return CompletableFutureExecutionFlow.just(((CompletionStage<Object>) o).toCompletableFuture());
                }
            };
        } else {
            throw new IllegalArgumentException("Unsupported filter return type " + type.getType().getName());
        }

    }

    record FilterMethod<T>(
        FilterOrder order,
        T bean,
        Executable<T, ?> method,
        boolean isResponseFilter,
        FilterArgBinder[] argBinders,
        @Nullable
        Predicate<FilterRunner> filterCondition,
        int passedOnContinuationArgIndex,
        FilterReturnHandler returnHandler
    ) implements GenericHttpFilter, Ordered {
        @Override
        public int getOrder() {
            return order.getOrder(bean);
        }

        private boolean invokeBefore(FilterRunner runner, @Nullable Executor completeOn) {
            FilterContinuationImpl<?> passedOnContinuation = null;
            try {
                if (filterCondition != null && !filterCondition.test(runner)) {
                    return true;
                }
                Object[] args = bindArgs(runner);
                if (passedOnContinuationArgIndex != -1) {
                    passedOnContinuation = (FilterContinuationImpl<?>) args[passedOnContinuationArgIndex];
                    if (completeOn != null) {
                        passedOnContinuation.completeOn = completeOn;
                    }
                }
                Object returnValue = method.invoke(bean, args);

                boolean proceed = returnHandler.handle(runner, returnValue, passedOnContinuation);
                if (passedOnContinuation != null && proceed) {
                    throw new AssertionError("handleFilterReturn should never return true if there is a continuation");
                }
                return proceed;
            } catch (Throwable e) {
                if (passedOnContinuation == null) {
                    runner.failure = e;
                    runner.responseNeedsProcessing = true;
                    runner.workResponse();
                } else {
                    passedOnContinuation.forwardResponse(null, e);
                }
                return false;
            }
        }

        private boolean invokeAfter(FilterRunner runner) {
            try {
                if (filterCondition != null && !filterCondition.test(runner)) {
                    return true;
                }
                Object returnValue = method.invoke(bean, bindArgs(runner));
                return returnHandler.handle(runner, returnValue, null);
            } catch (Throwable e) {
                runner.failure = e;
                runner.responseNeedsProcessing = true;
            }
            return true;
        }

        private Object[] bindArgs(FilterRunner runner) {
            Object[] args = new Object[argBinders.length];
            for (int i = 0; i < args.length; i++) {
                args[i] = argBinders[i].bind(runner);
            }
            return args;
        }
    }

    private interface FilterArgBinder {
        Object bind(FilterRunner runner);
    }

    private interface FilterReturnHandler {
        /**
         * Void method that accepts a continuation.
         */
        FilterReturnHandler VOID_WITH_CONTINUATION = (r, o, c) -> {
            // use response/failure from other filters
            c.forwardResponse(r.response, r.failure);
            return false;
        };
        /**
         * Void method.
         */
        FilterReturnHandler VOID = (r, o, c) -> true;
        /**
         * Request handler that returns a response but also accepts a continuation.
         */
        FilterReturnHandler FROM_REQUEST_RESPONSE_WITH_CONTINUATION = (r, o, c) -> {
            if (o == null) {
                // use response/failure from other filters
                c.forwardResponse(r.response, r.failure);
            } else {
                c.forwardResponse((HttpResponse<?>) o, null);
            }
            return false;
        };
        /**
         * Request handler that returns a new request.
         */
        FilterReturnHandler REQUEST = (r, o, c) -> {
            r.request = (HttpRequest<?>) Objects.requireNonNull(o, "Returned request must not be null, or mark the method as @Nullable");
            return true;
        };
        /**
         * Request handler that returns a new request (nullable).
         */
        FilterReturnHandler REQUEST_NULLABLE = (r, o, c) -> {
            if (o == null) {
                return true;
            }
            r.request = (HttpRequest<?>) o;
            return true;
        };
        /**
         * Request handler that returns a response.
         */
        FilterReturnHandler FROM_REQUEST_RESPONSE = (r, o, c) -> {
            // cancel request pipeline, move immediately to response handling
            r.response = (HttpResponse<?>) Objects.requireNonNull(o, "Returned response must not be null, or mark the method as @Nullable");
            r.failure = null;
            r.responseNeedsProcessing = true;
            r.workResponse();
            return false;
        };
        /**
         * Request handler that returns a response (nullable).
         */
        FilterReturnHandler FROM_REQUEST_RESPONSE_NULLABLE = (r, o, c) -> {
            if (o == null) {
                return true;
            }

            // cancel request pipeline, move immediately to response handling
            r.response = (HttpResponse<?>) o;
            r.failure = null;
            r.responseNeedsProcessing = true;
            r.workResponse();
            return false;
        };
        /**
         * Response handler that returns a new response.
         */
        FilterReturnHandler FROM_RESPONSE_RESPONSE = (r, o, c) -> {
            // cancel request pipeline, move immediately to response handling
            r.response = (HttpResponse<?>) Objects.requireNonNull(o, "Returned response must not be null, or mark the method as @Nullable");
            r.failure = null;
            r.responseNeedsProcessing = true;
            return true;
        };
        /**
         * Response handler that returns a new response (nullable).
         */
        FilterReturnHandler FROM_RESPONSE_RESPONSE_NULLABLE = (r, o, c) -> {
            if (o == null) {
                return true;
            }

            // cancel request pipeline, move immediately to response handling
            r.response = (HttpResponse<?>) o;
            r.failure = null;
            r.responseNeedsProcessing = true;
            return true;
        };

        boolean handle(FilterRunner runner, @Nullable Object o, FilterContinuationImpl<?> passedOnContinuation) throws Throwable;
    }

    private abstract static class DelayedFilterReturnHandler implements FilterReturnHandler {
        final boolean isResponseFilter;
        final FilterReturnHandler next;
        final boolean nullable;

        private DelayedFilterReturnHandler(boolean isResponseFilter, FilterReturnHandler next, boolean nullable) {
            this.isResponseFilter = isResponseFilter;
            this.next = next;
            this.nullable = nullable;
        }

        protected abstract ExecutionFlow<?> toFlow(FilterRunner runner, Object o);

        @Override
        public boolean handle(FilterRunner runner, Object o, FilterContinuationImpl<?> passedOnContinuation) throws Throwable {
            if (o == null && nullable) {
                return next.handle(runner, null, passedOnContinuation);
            }

            ExecutionFlow<?> delayedFlow = toFlow(runner, Objects.requireNonNull(o, "Returned value must not be null, or mark the method as @Nullable"));
            ImperativeExecutionFlow<?> doneFlow = delayedFlow.tryComplete();
            if (doneFlow != null) {
                if (doneFlow.getError() != null) {
                    throw doneFlow.getError();
                }
                return next.handle(runner, doneFlow.getValue(), passedOnContinuation);
            } else {
                // suspend until flow completes
                delayedFlow.onComplete((v, e) -> {
                    if (e == null) {
                        try {
                            if (next.handle(runner, v, passedOnContinuation)) {
                                if (isResponseFilter) {
                                    runner.workResponse();
                                } else {
                                    runner.workRequest();
                                }
                            }
                            return;
                        } catch (Throwable t) {
                            e = t;
                        }
                    }
                    if (passedOnContinuation == null) {
                        runner.failure = e;
                        runner.responseNeedsProcessing = true;
                        runner.workResponse();
                    } else {
                        passedOnContinuation.forwardResponse(null, e);
                    }
                });
                return false;
            }
        }
    }

    /**
     * Some filters are called for the request <i>and</i> the response (e.g. legacy, or "around"
     * filters), and need to carry state between the two. To achieve this, they are called once for
     * the request, where they register a SuspensionPoint. The SuspensionPoint is then completed
     * when the filter is reached for response processing. The filter has a listener on the
     * SuspensionPoint and handles further processing.<br>
     * There is also a special suspension point with filter index -1 which manages the flow
     * returned by {@link #run(HttpRequest)}.
     */
    private static class SuspensionPoint extends CompletableFuture<HttpResponse<?>> {
        /**
         * The index of the filter this suspension point is associated with.
         */
        final int filterIndex;
        /**
         * The next suspension point after this one. The invariant is that
         * {@code this.filterIndex > next.filterIndex}.
         */
        @Nullable
        final SuspensionPoint next;

        SuspensionPoint(int filterIndex, @Nullable SuspensionPoint next) {
            this.filterIndex = filterIndex;
            this.next = next;
        }
    }

    /**
     * This class implements the "continuation" request filter pattern. It is used by filters that
     * accept a {@link FilterContinuation}, but also by legacy {@link HttpFilter}s.<br>
     * Continuations give the user the choice when to proceed with filter execution. This adds some
     * difficulty for concurrency: we must ensure {@link #workRequest()} (or
     * {@link #workResponse()} in case of errors) is called exactly once. For this purpose, this
     * class has the {@link #downstreamGuard} flag. It is set to {@link true} when the filter
     * calls {@link #proceed}, or when the filter throws an exception. Only the first of these
     * events actually continues working the {@link FilterRunner}, any events that follow are
     * discarded (logged or throw an exception).
     *
     * @param <R> Return value of the continuation
     */
    private abstract class FilterContinuationImpl<R> extends SuspensionPoint implements FilterContinuation<R> {
        /**
         * This flag guards <i>downstream</i> execution, i.e. the call to {@link #workRequest()}
         * that runs downstream filters. Only the thread that claims this guard may run
         * {@link #workRequest()}.
         */
        final AtomicBoolean downstreamGuard = new AtomicBoolean(false);
        /**
         * This flag guards <i>upstream</i> execution, i.e. the call to {@link #workResponse()}
         * after the downstream has returned the response, or if the downstream pipeline was
         * aborted (usually because of an error).
         */
        final AtomicBoolean upstreamGuard = new AtomicBoolean(false);
        /**
         * Executor to run any downstream reactive code on. Only used by some implementations, e.g.
         * it doesn't make sense for a blocking continuation.
         */
        @Nullable
        Executor completeOn = null;

        FilterContinuationImpl(int filterIndex, @Nullable SuspensionPoint next) {
            super(filterIndex, next);
        }

        @Override
        public FilterContinuation<R> request(HttpRequest<?> request) {
            FilterRunner.this.request = Objects.requireNonNull(request, "request");
            return this;
        }

        final void triggerDownstreamWorkRequest() {
            if (downstreamGuard.compareAndSet(false, true)) {
                workRequest();
            } else {
                throw new IllegalStateException("Already subscribed to proceed() publisher, or filter method threw an exception and was cancelled");
            }
        }

        /**
         * Forward a given response from this suspension point. If {@link #proceed} was already
         * called, this waits for the downstream filters to finish.
         */
        final void forwardResponse(HttpResponse<?> response, Throwable failure) {
            if (downstreamGuard.compareAndSet(false, true)) {
                // proceed wasn't called, continue directly to response processing
                // cancel the suspension point, since we don't do more request processing
                responseSuspensionPoint = next;
            } else if (!isDone()) {
                // proceed was called, need to wait for completion to avoid concurrency issues
                // filters should really not do this: Either return before the proceed call, or
                // emit it from the returned publisher after proceed() is done
                whenComplete((resp, err) -> {
                    if (err == null) {
                        LOG.warn("Filter method returned early after chain.proceed() had already been called. This can lead to memory leaks, please fix your filter!");
                    } else {
                        LOG.warn("Filter method returned early after chain.proceed() had already been called. Downstream handlers also threw an exception, which is being discarded:", err);
                    }
                    forwardResponse(response, failure);
                });
                return;
            }
            // else proceed was called and the continuation was already done. The filter likely
            // already handled the response, and decided to continue based on it.

            if (!upstreamGuard.compareAndSet(false, true)) {
                if (failure == null) {
                    LOG.warn("Two outcomes for one continuation, this one is swallowed: {}", response);
                } else {
                    LOG.warn("Two outcomes for one continuation, this one is swallowed:", failure);
                }
                return;
            }
            FilterRunner.this.response = response;
            FilterRunner.this.failure = failure;
            responseNeedsProcessing = true;
            workResponse();
        }
    }

    /**
     * Continuation implementation that yields a reactive type.<br>
     * This class implements a bunch of interfaces that it would otherwise have to create lambdas
     * for.
     *
     * @param <R> The reactive type to return (e.g. Publisher, Mono, Flux...)
     */
    private class ReactiveContinuationImpl<R> extends FilterContinuationImpl<R> implements CorePublisher<HttpResponse<?>>, Subscription, BiConsumer<HttpResponse<?>, Throwable> {
        private final Class<R> reactiveType;
        private Subscriber<? super HttpResponse<?>> subscriber = null;
        private boolean addedListener = false;

        ReactiveContinuationImpl(int filterIndex, @Nullable SuspensionPoint next, Class<R> reactiveType) {
            super(filterIndex, next);
            this.reactiveType = reactiveType;
        }

        @Override
        public R proceed() {
            return Publishers.convertPublisher(conversionService, this, reactiveType);
        }

        @SuppressWarnings("NullableProblems")
        @Override
        public void subscribe(@NonNull CoreSubscriber<? super HttpResponse<?>> subscriber) {
            subscribe((Subscriber<? super HttpResponse<?>>) subscriber);
        }

        @Override
        public void subscribe(Subscriber<? super HttpResponse<?>> s) {
            if (this.subscriber != null) {
                throw new IllegalStateException("Only one subscriber allowed");
            }
            this.subscriber = s;

            if (s instanceof CoreSubscriber<?> cs) {
                FilterRunner.this.reactorContext = cs.currentContext();
            }

            triggerDownstreamWorkRequest();
            s.onSubscribe(this);
        }

        @Override
        public void request(long n) {
            if (n > 0 && !addedListener) {
                addedListener = true;
                if (completeOn == null) {
                    whenComplete(this);
                } else {
                    whenCompleteAsync(this, completeOn);
                }
            }
        }

        @Override
        public void cancel() {
            // ignored
        }

        @Override
        public void accept(HttpResponse<?> httpResponse, Throwable throwable) {
            try {
                if (throwable == null) {
                    subscriber.onNext(httpResponse);
                    subscriber.onComplete();
                } else {
                    subscriber.onError(throwable);
                }
            } catch (Throwable t) {
                LOG.warn("Subscriber threw exception", t);
            }
        }
    }

    /**
     * {@link FilterContinuationImpl} that is adapted for legacy filters: Implements
     * {@link FilterChain}, and also implements the {@link Subscriber} that will subscribe to the
     * {@link HttpFilter#doFilter} return value.
     */
    private class FilterChainImpl extends ReactiveContinuationImpl<Publisher<MutableHttpResponse<?>>> implements ClientFilterChain, ServerFilterChain, CoreSubscriber<HttpResponse<?>> {
        FilterChainImpl(int filterIndex, @Nullable SuspensionPoint next) {
            //noinspection unchecked,rawtypes
            super(filterIndex, next, (Class) Publisher.class);
        }

        @Override
        public Publisher<? extends HttpResponse<?>> proceed(MutableHttpRequest<?> request) {
            return proceed((HttpRequest<?>) request);
        }

        @Override
        public Publisher<MutableHttpResponse<?>> proceed(HttpRequest<?> request) {
            request(request);
            return proceed();
        }

        @Override
        public Publisher<MutableHttpResponse<?>> proceed() {
            // HACK: kotlin coroutine context propagation only supports reactor types (see
            // ReactorContextInjector). If we want to support our own type, we would need our own
            // ContextInjector, but that interface is marked as internal.
            // Another solution could be to PR kotlin to support all CorePublishers in
            // ReactorContextInjector.
            return Mono.from(super.proceed());
        }

        @SuppressWarnings("NullableProblems")
        @Override
        public void onSubscribe(@NonNull Subscription s) {
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(HttpResponse<?> response) {
            forwardResponse(response, null);
        }

        @Override
        public void onError(Throwable t) {
            forwardResponse(response, t);
        }

        @Override
        public void onComplete() {
            if (!upstreamGuard.get()) {
                forwardResponse(response, new IllegalStateException("Publisher did not return response"));
            }
        }

        @SuppressWarnings("NullableProblems")
        @NonNull
        @Override
        public Context currentContext() {
            return reactorContext;
        }
    }

    /**
     * Implementation of {@link FilterContinuation} for blocking calls.
     */
    private class BlockingContinuationImpl extends FilterContinuationImpl<HttpResponse<?>> {
        BlockingContinuationImpl(int filterIndex, @Nullable SuspensionPoint next) {
            super(filterIndex, next);
        }

        @Override
        public HttpResponse<?> proceed() {
            triggerDownstreamWorkRequest();

            boolean interrupted = false;
            while (true) {
                try {
                    // todo: detect event loop thread
                    HttpResponse<?> v = get();
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    return v;
                } catch (InterruptedException e) {
                    interrupted = true;
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException re) {
                        throw re;
                    } else {
                        throw new RuntimeException(cause);
                    }
                }
            }
        }
    }
}
