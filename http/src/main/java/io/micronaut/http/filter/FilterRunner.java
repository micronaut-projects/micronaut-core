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
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.CorePublisher;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The filter runner will start processing the filters in the forward order.
 * All the request filters are executed till one of them returns a response (bypasses the route execution for controllers or the client invocation),
 * or the terminal filter will produce the response from the route/client call.
 * After that, the filters are processed in the opposite order so response filters can be processed,
 * which can sometimes override the existing response.
 * There is a special case of response filters that needs to process the response; for those cases,
 * the filter needs to be suspended, and the next filter in the order needs to be executed.
 * When the response is committed, the filter will be resumed when it's processed again.
 * There is a special case for the client filters; those will process the exception,
 * which needs to be tracked during the response filtering phase.
 *
 * @author Jonas Konrad
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public class FilterRunner {
    private static final Logger LOG = LoggerFactory.getLogger(FilterRunner.class);
    private static final Predicate<FilterMethodContext> FILTER_CONDITION_ALWAYS_TRUE = runner -> true;

    private final ConversionService conversionService;
    /**
     * All filters to run. Request filters are executed in order from first to last, response
     * filters in the reverse order.
     */
    private final List<GenericHttpFilter> filters;

    private Context initialReactorContext = Context.empty();

    /**
     * Create a new filter runner, to be used only once.
     *
     * @param conversionService The conversion service
     * @param filters           The filters to run
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
     * @param request The current request
     * @param failure The failure
     * @return A flow that will be passed on to the next filter
     */
    protected ExecutionFlow<? extends HttpResponse<?>> processFailure(HttpRequest<?> request, Throwable failure) {
        return ExecutionFlow.error(failure);
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
        this.initialReactorContext = reactorContext;
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
        return (ExecutionFlow) filterRequest(new FilterContext(request, initialReactorContext), filters.listIterator(), new HashMap<>());
    }

    private ExecutionFlow<HttpResponse<?>> filterRequest(FilterContext context,
                                                         ListIterator<GenericHttpFilter> iterator,
                                                         Map<GenericHttpFilter, Map.Entry<ExecutionFlow<FilterContext>, FilterContinuationImpl<?>>> suspended) {
        return filterRequest0(context, iterator, suspended)
                .flatMap(newContext -> {
                    if (newContext.response != null) {
                        return filterResponse(newContext, iterator, null, suspended);
                    }
                    return ExecutionFlow.error(new IllegalStateException("Request filters didn't produce any response!"));
                });
    }

    private ExecutionFlow<FilterContext> filterRequest0(FilterContext context,
                                                        ListIterator<GenericHttpFilter> iterator,
                                                        Map<GenericHttpFilter, Map.Entry<ExecutionFlow<FilterContext>, FilterContinuationImpl<?>>> suspended) {
        if (context.response != null) {
            return ExecutionFlow.just(context);
        }
        if (iterator.hasNext()) {
            GenericHttpFilter filter = iterator.next();
            return processRequestFilter(filter, context, suspended)
                    .flatMap(newContext -> filterRequest0(newContext, iterator, suspended))
                    .onErrorResume(throwable -> {
                        // Un-suspend possibly awaiting filter and exception filtering scenario of the http client
                        return filterResponse(context, iterator, throwable, suspended).map(context::withResponse);
                    });
        } else {
            return ExecutionFlow.error(new IllegalStateException("Request filters didn't produce any response!"));
        }
    }

    private ExecutionFlow<HttpResponse<?>> filterResponse(FilterContext context,
                                                          ListIterator<GenericHttpFilter> iterator,
                                                          @Nullable
                                                          Throwable exception,
                                                          Map<GenericHttpFilter, Map.Entry<ExecutionFlow<FilterContext>, FilterContinuationImpl<?>>> suspended) {
        if (iterator.hasPrevious()) {
            // Walk backwards and execute response filters or un-suspend request filters waiting for the response
            GenericHttpFilter filter = iterator.previous();
            return processResponseFilter(filter, context, exception, suspended)
                    .flatMap(newContext -> {
                        if (context != newContext) {
                            return processResponse(newContext.request, newContext.response).map(context::withResponse);
                        }
                        return ExecutionFlow.just(newContext);
                    })
                    .onErrorResume(throwable -> processFailure(context.request, throwable).map(context::withResponse))
                    .flatMap(newContext -> filterResponse(newContext, iterator, newContext.response == null ? exception : null, suspended));
        } else if (context.response != null) {
            return ExecutionFlow.just(context.response);
        } else if (exception != null) {
            // This scenario only applies for client filters
            // Filters didn't remap the exception to any response
            return ExecutionFlow.error(exception);
        } else {
            return ExecutionFlow.error(new IllegalStateException("No response after response filters completed!"));
        }
    }

    private ExecutionFlow<FilterContext> processRequestFilter(GenericHttpFilter filter,
                                                              FilterContext context,
                                                              Map<GenericHttpFilter, Map.Entry<ExecutionFlow<FilterContext>,
                                                                      FilterContinuationImpl<?>>> suspended) {
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
                return ExecutionFlow.just(context);
            }
            ExecutionFlow<FilterContext> filterMethodFlow;
            FilterContinuationImpl<?> continuation = before.isSuspended() ? before.createContinuation(context) : null;
            FilterMethodContext filterMethodContext = new FilterMethodContext(
                    context.request,
                    context.response,
                    null,
                    continuation);
            if (executeOn == null) {
                // possibly continue with next filter
                filterMethodFlow = before.filter(context, filterMethodContext);
            } else {
                if (continuation != null) {
                    continuation.completeOn = executeOn;
                }
                filterMethodFlow = ExecutionFlow.async(executeOn, () -> before.filter(context, filterMethodContext));
            }
            if (before.isSuspended()) {
                if (continuation instanceof ReactiveResultAwareReactiveContinuationImpl<?>) {
                    // Method consumes reactive continuation and returns reactive result
                    suspended.put(filter, Map.entry(continuation.filterProcessedFlow(), continuation));
                } else if (continuation instanceof ReactiveContinuationImpl<?>) {
                    // Method consumes reactive continuation and doesn't return reactive result
                    throw new IllegalStateException("Not supported use-case with reactive continuation and non-reactive return type");
                } else {
                    // Method consumes blocking continuation
                    suspended.put(filter, Map.entry(filterMethodFlow, continuation));
                }
                // Continue executing other filters while this one is suspended
                return continuation.nextFilterFlow();
            }
            return filterMethodFlow;
        } else if (filter instanceof GenericHttpFilter.AroundLegacy around) {
            FilterChainImpl chainSuspensionPoint = new FilterChainImpl(conversionService, context);
            // Legacy `Publisher<HttpResponse> proceed(..)` filters are always suspended
            suspended.put(around, Map.entry(chainSuspensionPoint.filterProcessedFlow(), chainSuspensionPoint));
            chainSuspensionPoint.completeOn = executeOn;
            if (executeOn == null) {
                try {
                    around.bean().doFilter(context.request, chainSuspensionPoint).subscribe(chainSuspensionPoint);
                } catch (Throwable e) {
                    chainSuspensionPoint.triggerFilterProcessed(context, null, e);
                }
                return chainSuspensionPoint.nextFilterFlow();
            } else {
                return ExecutionFlow.async(executeOn, () -> {
                    try {
                        around.bean().doFilter(context.request, chainSuspensionPoint).subscribe(chainSuspensionPoint);
                    } catch (Throwable e) {
                        chainSuspensionPoint.triggerFilterProcessed(context, null, e);
                    }
                    return chainSuspensionPoint.nextFilterFlow();
                });
            }
        } else if (filter instanceof GenericHttpFilter.TerminalReactive || filter instanceof GenericHttpFilter.Terminal || filter instanceof GenericHttpFilter.TerminalWithReactorContext) {
            if (executeOn != null) {
                throw new IllegalStateException("Async terminal filters not supported");
            }
            if (filter.isSuspended()) {
                throw new IllegalStateException("Terminal filters cannot be suspended");
            }
            ExecutionFlow<? extends HttpResponse<?>> terminalFlow;
            if (filter instanceof GenericHttpFilter.TerminalWithReactorContext t) {
                try {
                    terminalFlow = t.execute(context.request, context.reactorContext);
                } catch (Throwable e) {
                    terminalFlow = ExecutionFlow.error(e);
                }
            } else if (filter instanceof GenericHttpFilter.Terminal t) {
                try {
                    terminalFlow = t.execute(context.request);
                } catch (Throwable e) {
                    terminalFlow = ExecutionFlow.error(e);
                }
            } else {
                terminalFlow = ReactiveExecutionFlow.fromPublisher(Mono.from(((GenericHttpFilter.TerminalReactive) filter).responsePublisher())
                        .contextWrite(context.reactorContext));
            }
            return terminalFlow.flatMap(response -> ExecutionFlow.just(context.withResponse(response)));
        } else {
            throw new IllegalStateException("Unknown filter type");
        }
    }

    private ExecutionFlow<FilterContext> processResponseFilter(GenericHttpFilter filter,
                                                               FilterContext filterContext,
                                                               Throwable exceptionToFilter,
                                                               Map<GenericHttpFilter, Map.Entry<ExecutionFlow<FilterContext>, FilterContinuationImpl<?>>> suspended) {
        Executor executeOn;
        if (filter instanceof GenericHttpFilter.Async async) {
            executeOn = async.executor();
            filter = async.actual();
        } else {
            executeOn = null;
        }

        Map.Entry<ExecutionFlow<FilterContext>, FilterContinuationImpl<?>> suspendedFilterData = suspended.get(filter);
        if (suspendedFilterData != null) {
            // This filter is suspended and awaiting to receive the response
            ExecutionFlow<FilterContext> filterProcessedFlow = suspendedFilterData.getKey();
            FilterContinuationImpl<?> continuation = suspendedFilterData.getValue();
            // Resume suspended filter
            continuation.resume(filterContext, exceptionToFilter);
            // Filter flow might modify the context provided
            return filterProcessedFlow;
        }

        if (exceptionToFilter != null && !filter.isFiltersException()) {
            return ExecutionFlow.just(filterContext);
        }

        if (filter instanceof FilterMethod<?> after && after.isResponseFilter) {
            if (after.isSuspended()) {
                return ExecutionFlow.error(new IllegalStateException("Response filter cannot have a continuation!"));
            }
            FilterMethodContext filterMethodContext = new FilterMethodContext(
                    filterContext.request,
                    filterContext.response,
                    exceptionToFilter,
                    null);
            if (executeOn == null) {
                return after.filter(filterContext, filterMethodContext);
            } else {
                return ExecutionFlow.async(executeOn, () -> after.filter(filterContext, filterMethodContext));
            }
        }
        return ExecutionFlow.just(filterContext);
    }

    @Internal
    public static <T> FilterMethod<T> prepareFilterMethod(ConversionService conversionService,
                                                          T bean,
                                                          ExecutableMethod<T, ?> method,
                                                          boolean isResponseFilter,
                                                          FilterOrder order) throws IllegalArgumentException {
        return prepareFilterMethod(conversionService, bean, method, method.getArguments(), method.getReturnType().asArgument(), isResponseFilter, order);
    }

    @Internal
    public static void validateFilterMethod(Argument<?>[] arguments,
                                                Argument<?> returnType,
                                                boolean isResponseFilter) throws IllegalArgumentException {
        prepareFilterMethod(ConversionService.SHARED, null, null, arguments, returnType, isResponseFilter, null);
    }

    @Internal
    public static <T> FilterMethod<T> prepareFilterMethod(ConversionService conversionService,
                                                          T bean,
                                                          ExecutableMethod<T, ?> method,
                                                          Argument<?>[] arguments,
                                                          Argument<?> returnType,
                                                          boolean isResponseFilter,
                                                          FilterOrder order) throws IllegalArgumentException {
        FilterArgBinder[] fulfilled = new FilterArgBinder[arguments.length];
        Predicate<FilterMethodContext> filterCondition = FILTER_CONDITION_ALWAYS_TRUE;
        boolean skipOnError = isResponseFilter;
        boolean filtersException = false;
        Function<FilterContext, FilterContinuationImpl<?>> continuationCreator = null;
        for (int i = 0; i < arguments.length; i++) {
            Argument<?> argument = arguments[i];
            if (argument.getType().isAssignableFrom(HttpRequest.class)) {
                fulfilled[i] = ctx -> ctx.request;
            } else if (argument.getType().isAssignableFrom(MutableHttpRequest.class)) {
                fulfilled[i] = ctx -> {
                    HttpRequest<?> request = ctx.request;
                    if (!(ctx.request instanceof MutableHttpRequest<?>)) {
                        request = ctx.request.mutate();
                    }
                    return request;
                };
            } else if (argument.getType().isAssignableFrom(MutableHttpResponse.class)) {
                if (!isResponseFilter) {
                    throw new IllegalArgumentException("Filter is called before the response is known, can't have a response argument");
                }
                fulfilled[i] = ctx -> ctx.response;
            } else if (Throwable.class.isAssignableFrom(argument.getType())) {
                if (!isResponseFilter) {
                    throw new IllegalArgumentException("Request filters cannot handle exceptions");
                }
                if (!argument.isNullable()) {
                    filterCondition = filterCondition.and(ctx -> ctx.failure != null && argument.isInstance(ctx.failure));
                    fulfilled[i] = ctx -> ctx.failure;
                } else {
                    fulfilled[i] = ctx -> {
                        if (ctx.failure != null && argument.isInstance(ctx.failure)) {
                            return ctx.failure;
                        }
                        return null;
                    };
                }
                filtersException = true;
                skipOnError = false;
            } else if (argument.getType() == FilterContinuation.class) {
                if (isResponseFilter) {
                    throw new IllegalArgumentException("Response filters cannot use filter continuations");
                }
                if (continuationCreator != null) {
                    throw new IllegalArgumentException("Only one continuation per filter is allowed");
                }
                Argument<?> continuationReturnType = argument.getFirstTypeVariable().orElseThrow(() -> new IllegalArgumentException("Continuations must specify generic type"));
                if (isReactive(continuationReturnType) && continuationReturnType.getWrappedType().isAssignableFrom(MutableHttpResponse.class)) {
                    if (isReactive(returnType)) {
                        continuationCreator = ctx -> new ReactiveResultAwareReactiveContinuationImpl<>(conversionService, ctx);
                    } else {
                        continuationCreator = ctx -> new ReactiveContinuationImpl<>(conversionService, ctx, continuationReturnType.getType());
                    }
                    fulfilled[i] = ctx -> ctx.continuation;
                } else if (continuationReturnType.getType().isAssignableFrom(MutableHttpResponse.class)) {
                    continuationCreator = BlockingContinuationImpl::new;
                    fulfilled[i] = ctx -> ctx.continuation;
                } else {
                    throw new IllegalArgumentException("Unsupported continuation type: " + continuationReturnType);
                }
            } else {
                throw new IllegalArgumentException("Unsupported filter argument type: " + argument);
            }
        }
        if (skipOnError) {
            filterCondition = filterCondition.and(ctx -> ctx.failure == null);
        } else if (filterCondition == FILTER_CONDITION_ALWAYS_TRUE) {
            filterCondition = null;
        }
        FilterReturnHandler returnHandler = prepareReturnHandler(conversionService, returnType, isResponseFilter, continuationCreator != null, false);
        return new FilterMethod<>(
                order,
                bean,
                method,
                isResponseFilter,
                fulfilled,
                filterCondition,
                continuationCreator,
                filtersException,
                returnHandler
        );
    }

    private static boolean isReactive(Argument<?> continuationReturnType) {
        // Argument.isReactive doesn't work in http-validation, this is a workaround
        return continuationReturnType.isReactive() || continuationReturnType.getType() == Publisher.class;
    }

    private static FilterReturnHandler prepareReturnHandler(ConversionService conversionService,
                                                            Argument<?> type,
                                                            boolean isResponseFilter,
                                                            boolean hasContinuation,
                                                            boolean fromOptional) throws IllegalArgumentException {
        if (type.isOptional()) {
            FilterReturnHandler next = prepareReturnHandler(conversionService, type.getWrappedType(), isResponseFilter, hasContinuation, true);
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
            var next = prepareReturnHandler(conversionService, type.getWrappedType(), isResponseFilter, hasContinuation, false);
            return new FilterReturnHandler() {

                @Override
                public ExecutionFlow<FilterContext> handle(FilterContext context, Object returnValue, FilterContinuationImpl<?> continuation) throws Throwable {
                    if (returnValue == null && !nullable) {
                        return next.handle(context, null, continuation);
                    }

                    Mono publisher = Mono.from(Publishers.convertPublisher(conversionService, returnValue, Publisher.class))
                            .contextWrite(context.reactorContext());

                    if (continuation instanceof ReactiveResultAwareReactiveContinuationImpl<?> reactiveContinuation) {
                        publisher.subscribe(reactiveContinuation);
                        return reactiveContinuation.nextFilterFlow();
                    }
                    return ReactiveExecutionFlow.fromPublisher(publisher).flatMap(v -> {
                        try {
                            return next.handle(context, v, continuation);
                        } catch (Throwable e) {
                            return ExecutionFlow.error(e);
                        }
                    });
                }

            };
        } else if (type.isAsync()) {
            var next = prepareReturnHandler(conversionService, type.getWrappedType(), isResponseFilter, hasContinuation, false);
            return new DelayedFilterReturnHandler(isResponseFilter, next, nullable) {
                @Override
                protected ExecutionFlow<?> toFlow(FilterContext context, Object returnValue, FilterContinuationImpl<?> continuation) {
                    //noinspection unchecked
                    return CompletableFutureExecutionFlow.just(((CompletionStage<Object>) returnValue).toCompletableFuture());
                }
            };
        } else {
            throw new IllegalArgumentException("Unsupported filter return type " + type.getType().getName());
        }
    }

    record FilterMethod<T>(FilterOrder order,
                           T bean,
                           Executable<T, ?> method,
                           boolean isResponseFilter,
                           FilterArgBinder[] argBinders,
                           @Nullable
                           Predicate<FilterMethodContext> filterCondition,
                           Function<FilterContext, FilterContinuationImpl<?>> continuationCreator,
                           boolean filtersException,
                           FilterReturnHandler returnHandler
    ) implements GenericHttpFilter, Ordered {

        @Override
        public boolean isSuspended() {
            return continuationCreator != null;
        }

        @Override
        public boolean isFiltersException() {
            return filtersException;
        }

        @Override
        public int getOrder() {
            return order.getOrder(bean);
        }

        public FilterContinuationImpl<?> createContinuation(FilterContext filterContext) {
            return continuationCreator.apply(filterContext);
        }

        private ExecutionFlow<FilterContext> filter(FilterContext filterContext,
                                                    FilterMethodContext methodContext) {
            try {
                if (filterCondition != null && !filterCondition.test(methodContext)) {
                    return ExecutionFlow.just(filterContext);
                }
                Object[] args = bindArgs(methodContext);
                Object returnValue = method.invoke(bean, args);
                return returnHandler.handle(filterContext, returnValue, methodContext.continuation);
            } catch (Throwable e) {
                if (methodContext.continuation != null) {
                    return methodContext.continuation.afterMethodExecuted(e);
                }
                return ExecutionFlow.error(e);
            }
        }

        private Object[] bindArgs(FilterMethodContext context) {
            Object[] args = new Object[argBinders.length];
            for (int i = 0; i < args.length; i++) {
                args[i] = argBinders[i].bind(context);
            }
            return args;
        }

    }

    private record FilterMethodContext(
            HttpRequest<?> request,
            @Nullable HttpResponse<?> response,
            @Nullable Throwable failure,
            @Nullable FilterContinuationImpl<?> continuation) {
    }

    private interface FilterArgBinder {
        Object bind(FilterMethodContext context);
    }

    private interface FilterReturnHandler {
        /**
         * Void method that accepts a continuation.
         */
        FilterReturnHandler VOID_WITH_CONTINUATION = (filterContext, returnValue, continuation) -> continuation.afterMethodExecuted();
        /**
         * Void method.
         */
        FilterReturnHandler VOID = (filterContext, returnValue, continuation) -> ExecutionFlow.just(filterContext);
        /**
         * Request handler that returns a response but also accepts a continuation.
         */
        FilterReturnHandler FROM_REQUEST_RESPONSE_WITH_CONTINUATION = (filterContext, returnValue, continuation) -> {
            if (returnValue == null) {
                return continuation.afterMethodExecuted();
            } else {
                return continuation.afterMethodExecuted((HttpResponse<?>) returnValue);
            }
        };
        /**
         * Request handler that returns a new request.
         */
        FilterReturnHandler REQUEST = (filterContext, returnValue, continuation) -> ExecutionFlow.just(
                filterContext.withRequest(
                        (HttpRequest<?>) Objects.requireNonNull(returnValue, "Returned request must not be null, or mark the method as @Nullable")
                )
        );
        /**
         * Request handler that returns a new request (nullable).
         */
        FilterReturnHandler REQUEST_NULLABLE = (filterContext, returnValue, continuation) -> {
            if (returnValue == null) {
                return ExecutionFlow.just(filterContext);
            }
            return ExecutionFlow.just(
                    filterContext.withRequest((HttpRequest<?>) returnValue)
            );
        };
        /**
         * Request handler that returns a response.
         */
        FilterReturnHandler FROM_REQUEST_RESPONSE = (filterContext, returnValue, continuation) -> {
            // cancel request pipeline, move immediately to response handling
            return ExecutionFlow.just(
                    filterContext
                            .withResponse(
                                    (HttpResponse<?>) Objects.requireNonNull(returnValue, "Returned response must not be null, or mark the method as @Nullable")
                            )
            );
        };
        /**
         * Request handler that returns a response (nullable).
         */
        FilterReturnHandler FROM_REQUEST_RESPONSE_NULLABLE = (filterContext, returnValue, continuation) -> {
            if (returnValue == null) {
                return ExecutionFlow.just(filterContext);
            }
            // cancel request pipeline, move immediately to response handling
            return ExecutionFlow.just(
                    filterContext.withResponse((HttpResponse<?>) returnValue)
            );
        };
        /**
         * Response handler that returns a new response.
         */
        FilterReturnHandler FROM_RESPONSE_RESPONSE = (filterContext, returnValue, continuation) -> {
            // cancel request pipeline, move immediately to response handling
            return ExecutionFlow.just(
                    filterContext
                            .withResponse(
                                    (HttpResponse<?>) Objects.requireNonNull(returnValue, "Returned response must not be null, or mark the method as @Nullable")
                            )
            );
        };
        /**
         * Response handler that returns a new response (nullable).
         */
        FilterReturnHandler FROM_RESPONSE_RESPONSE_NULLABLE = (filterContext, returnValue, continuation) -> {
            if (returnValue == null) {
                return ExecutionFlow.just(filterContext);
            }
            // cancel request pipeline, move immediately to response handling
            return ExecutionFlow.just(
                    filterContext.withResponse((HttpResponse<?>) returnValue)
            );
        };

        ExecutionFlow<FilterContext> handle(FilterContext context,
                                            @Nullable Object returnValue,
                                            @Nullable FilterContinuationImpl<?> passedOnContinuation) throws Throwable;
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

        protected abstract ExecutionFlow<?> toFlow(FilterContext context,
                                                   Object returnValue,
                                                   @Nullable FilterContinuationImpl<?> continuation);

        @Override
        public ExecutionFlow<FilterContext> handle(FilterContext context,
                                                   @Nullable Object returnValue,
                                                   FilterContinuationImpl<?> continuation) throws Throwable {
            if (returnValue == null && nullable) {
                return next.handle(context, null, continuation);
            }

            ExecutionFlow<?> delayedFlow = toFlow(context,
                    Objects.requireNonNull(returnValue, "Returned value must not be null, or mark the method as @Nullable"),
                    continuation
            );
            ImperativeExecutionFlow<?> doneFlow = delayedFlow.tryComplete();
            if (doneFlow != null) {
                if (doneFlow.getError() != null) {
                    throw doneFlow.getError();
                }
                return next.handle(context, doneFlow.getValue(), continuation);
            } else {
                return delayedFlow.flatMap(v -> {
                    try {
                        return next.handle(context, v, continuation);
                    } catch (Throwable e) {
                        return ExecutionFlow.error(e);
                    }
                });
            }
        }
    }

    /**
     * This class implements the "continuation" request filter pattern. It is used by filters that
     * accept a {@link FilterContinuation}, but also by legacy {@link HttpFilter}s.<br>
     * Continuations give the user the choice when to proceed with filter execution.
     * After the proceed is triggered the filter is essentially suspended and the next filter in the chain should be executed.
     *
     * @param <R> Return value of the continuation
     */
    private abstract static class FilterContinuationImpl<R> implements FilterContinuation<R> {

        /**
         * Executor to run any downstream reactive code on. Only used by some implementations, e.g.
         * it doesn't make sense for a blocking continuation.
         */
        @Nullable
        Executor completeOn = null;

        FilterContext filterContext;

        /**
         * The future indicating that the next filter should be executed.
         */
        final CompletableFuture<FilterContext> nextFilterProcessing = new CompletableFuture<>();
        /**
         * The future representing the suspension point, completing it will resume this filter processing.
         */
        final CompletableFuture<FilterContext> suspensionPoint = new CompletableFuture<>();
        /**
         * The future representing the filter return value and will be completed when the filter method is finally processed.
         */
        final CompletableFuture<FilterContext> filterProcessed = new CompletableFuture<>();

        FilterContinuationImpl(FilterContext filterContext) {
            this.filterContext = filterContext;
        }

        @Override
        public FilterContinuation<R> request(HttpRequest<?> request) {
            filterContext = filterContext.withRequest(Objects.requireNonNull(request, "request"));
            return this;
        }

        protected final void proceedRequested() {
            if (!nextFilterProcessing.isDone()) {
                nextFilterProcessing.complete(filterContext);
            } else {
                throw new IllegalStateException("Already subscribed to proceed() publisher, or filter method threw an exception and was cancelled");
            }
        }

        /**
         * The filter is suspended. After this filter is ready returned flow will process a next filter.
         */
        public ExecutionFlow<FilterContext> nextFilterFlow() {
            return CompletableFutureExecutionFlow.just(nextFilterProcessing);
        }

        /**
         * The flow to continue after the suspended filter is finished.
         */
        public ExecutionFlow<FilterContext> filterProcessedFlow() {
            return CompletableFutureExecutionFlow.just(filterProcessed);
        }

        /**
         * Resume suspended method with a new context.
         *
         * @param filterContext The context to resume the suspend method.
         * @param throwable     The exception
         */
        public void resume(FilterContext filterContext, Throwable throwable) {
            if (!suspensionPoint.isDone()) {
                if (throwable == null) {
                    suspensionPoint.complete(filterContext);
                } else {
                    suspensionPoint.completeExceptionally(throwable);
                }
            } else {
                if (throwable == null) {
                    LOG.warn("Two outcomes for one continuation, this one is swallowed: {}", filterContext.response);
                } else {
                    LOG.warn("Two outcomes for one continuation, this one is swallowed:", throwable);
                }
            }
        }

        /**
         * The filter method completed without modifying response / failed status.
         */
        private ExecutionFlow<FilterContext> afterMethodExecuted() {
            return afterMethodExecuted(null, null);
        }

        /**
         * The filter method completed with modified response.
         */
        private ExecutionFlow<FilterContext> afterMethodExecuted(@NonNull HttpResponse<?> response) {
            return afterMethodExecuted(response, null);
        }

        /**
         * The filter method completed with a failure.
         */
        ExecutionFlow<FilterContext> afterMethodExecuted(@NonNull Throwable throwable) {
            return afterMethodExecuted(null, throwable);
        }

        /**
         * Forward a given response from this suspension point. If {@link #proceed} was already
         * called, this waits for the downstream filters to finish.
         */
        private ExecutionFlow<FilterContext> afterMethodExecuted(@Nullable HttpResponse<?> newResponse,
                                                                 @Nullable Throwable newFailure) {
            FilterContext newFilterContext;
            if (suspensionPoint.isDone()) {
                // If the method modifies the response / failure, extend its filter context for downstream
                // This is blocking scenario
                try {
                    newFilterContext = suspensionPoint.get();
                } catch (Exception e) {
                    return ExecutionFlow.error(new IllegalStateException("Failed to extract suspension point result", e));
                }
            } else {
                newFilterContext = filterContext;
            }
            return asFilterProcessed(newFilterContext, newResponse, newFailure);
        }

        protected void triggerFilterProcessed(FilterContext filterContext,
                                              @Nullable
                                              HttpResponse<?> newResponse,
                                              @Nullable
                                              Throwable newFailure) {
            if (!nextFilterProcessing.isDone()) {
                // Publish the error to the nextFilterProcessing as well
                if (newFailure == null) {
                    nextFilterProcessing.complete(newResponse == null ? filterContext : filterContext.withResponse(newResponse));
                } else {
                    nextFilterProcessing.completeExceptionally(newFailure);
                }
            }
            if (!filterProcessed.isDone()) {
                if (newFailure == null) {
                    filterProcessed.complete(newResponse == null ? filterContext : filterContext.withResponse(newResponse));
                } else {
                    filterProcessed.completeExceptionally(newFailure);
                }
            } else {
                if (newFailure == null) {
                    LOG.warn("Two outcomes for one continuation, this one is swallowed: {}", newResponse);
                } else {
                    LOG.warn("Two outcomes for one continuation, this one is swallowed:", newFailure);
                }
            }
        }

        @NotNull
        private ExecutionFlow<FilterContext> asFilterProcessed(FilterContext filterContext,
                                                               @Nullable
                                                               HttpResponse<?> newResponse,
                                                               @Nullable
                                                               Throwable newFailure) {
            triggerFilterProcessed(filterContext, newResponse, newFailure);
            return CompletableFutureExecutionFlow.just(filterProcessed);
        }

    }

    private record FilterContext(HttpRequest<?> request,
                                 @Nullable HttpResponse<?> response,
                                 Context reactorContext) {

        FilterContext(HttpRequest<?> request, Context reactorContext) {
            this(request, null, reactorContext);
        }

        public FilterContext withRequest(@NonNull HttpRequest<?> request) {
            if (this.request == request) {
                return this;
            }
            if (response != null) {
                throw new IllegalStateException("Cannot modify the request after response is set!");
            }
            Objects.requireNonNull(request);
            return new FilterContext(request, response, reactorContext);
        }

        public FilterContext withResponse(@NonNull HttpResponse<?> response) {
            if (this.response == response) {
                return this;
            }
            Objects.requireNonNull(response);
            // New response should remove the failure
            return new FilterContext(request, response, reactorContext);
        }

        public FilterContext withReactorContext(@NonNull Context reactorContext) {
            if (this.reactorContext == reactorContext) {
                return this;
            }
            Objects.requireNonNull(reactorContext);
            return new FilterContext(request, response, reactorContext);
        }

    }

    /**
     * Continuation implementation that yields a reactive type.<br>
     * This class implements a bunch of interfaces that it would otherwise have to create lambdas
     * for.
     *
     * @param <R> The reactive type to return (e.g. Publisher, Mono, Flux...)
     */
    private static class ReactiveContinuationImpl<R> extends FilterContinuationImpl<R>
            implements CorePublisher<HttpResponse<?>>, Subscription, BiConsumer<FilterContext, Throwable> {
        private final ConversionService conversionService;
        private final Class<R> reactiveType;
        private Subscriber<? super HttpResponse<?>> subscriber = null;
        private boolean addedListener = false;

        ReactiveContinuationImpl(ConversionService conversionService, FilterContext filterContext, Class<R> reactiveType) {
            super(filterContext);
            this.conversionService = conversionService;
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
                filterContext = filterContext.withReactorContext(cs.currentContext());
            }

            proceedRequested();
            s.onSubscribe(this);
        }

        @Override
        public void request(long n) {
            if (n > 0 && !addedListener) {
                addedListener = true;
                if (completeOn == null) {
                    suspensionPoint.whenComplete(this);
                } else {
                    suspensionPoint.whenCompleteAsync(this, completeOn);
                }
            }
        }

        @Override
        public void cancel() {
            // ignored
        }

        @Override
        public void accept(FilterContext filterContext, Throwable throwable) {
            // Suspension point resumed
            try {
                if (throwable == null) {
                    this.filterContext = filterContext;
                    subscriber.onNext(filterContext.response);
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
     * {@link FilterContinuationImpl} that is adapted for filters returning a reactive response .
     * Implements the {@link Subscriber} that will subscribe to the method's return value.
     *
     * @param <T> The published item type
     */
    private static class ReactiveResultAwareReactiveContinuationImpl<T> extends ReactiveContinuationImpl<Publisher<T>>
            implements CoreSubscriber<HttpResponse<?>> {

        ReactiveResultAwareReactiveContinuationImpl(ConversionService conversionService, FilterContext filterContext) {
            //noinspection unchecked,rawtypes
            super(conversionService, filterContext, (Class) Publisher.class);
        }

        @Override
        public Publisher<T> proceed() {
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
            triggerFilterProcessed(filterContext, response, null);
        }

        @Override
        public void onError(Throwable t) {
            triggerFilterProcessed(filterContext, null, t);
        }

        @Override
        public void onComplete() {
            if (!suspensionPoint.isDone()) {
                triggerFilterProcessed(filterContext, null, new IllegalStateException("Publisher did not return response"));
            }
        }

        @SuppressWarnings("NullableProblems")
        @NonNull
        @Override
        public Context currentContext() {
            return filterContext.reactorContext;
        }
    }

    /**
     * {@link ReactiveResultAwareReactiveContinuationImpl} that is adapted for legacy filters: Implements {@link FilterChain}.
     */
    private static final class FilterChainImpl extends ReactiveResultAwareReactiveContinuationImpl<MutableHttpResponse<?>>
            implements ClientFilterChain, ServerFilterChain {
        FilterChainImpl(ConversionService conversionService, FilterContext filterContext) {
            super(conversionService, filterContext);
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

    }

    /**
     * Implementation of {@link FilterContinuation} for blocking calls.
     */
    private static final class BlockingContinuationImpl extends FilterContinuationImpl<HttpResponse<?>> {
        BlockingContinuationImpl(FilterContext filterContext) {
            super(filterContext);
        }

        @Override
        public HttpResponse<?> proceed() {
            proceedRequested();

            boolean interrupted = false;
            while (true) {
                try {
                    // todo: detect event loop thread
                    filterContext = suspensionPoint.get();
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    return filterContext.response;
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
