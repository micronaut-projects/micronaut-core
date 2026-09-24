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
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.execution.CompletableFutureExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.execution.ImperativeExecutionFlow;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.core.type.UnsafeExecutable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.http.reactive.execution.SubscriberAwareExecutionFlow;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.NonBlocking;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Internal implementation of {@link io.micronaut.http.annotation.ServerFilter}.
 *
 * @param <T>                 The bean type
 * @param order               The order
 * @param bean                The bean instance
 * @param method              The method
 * @param unsafeExecutable    The optional unsafe method executor
 * @param isResponseFilter    If it's a response filter
 * @param argBinders          The argument binders
 * @param asyncArgBinders     The asynchronous argument binders, or {@code null} if all args are sync
 * @param filterCondition     The filter condition
 * @param continuationCreator The continuation creator
 * @param filtersException    The filter exception
 * @param returnHandler       The return handler
 * @param isConditional       Is conditional filter
 * @param executor            The executor to run this filter on
 * @param isReactive          Is the filter method reactive, either by its return type or its continuation
 * @author Jonas Konrad
 * @author Denis Stepanov
 * @since 4.2.0
 */
@SuppressWarnings("java:S6218") // equals/hashCode not used
@Internal
record MethodFilter<T>(FilterOrder order,
                       T bean,
                       Executable<T, ?> method,
                       @Nullable
                       UnsafeExecutable<T, ?> unsafeExecutable,
                       boolean isResponseFilter,
                       @Nullable FilterArgBinder[] argBinders,
                       @Nullable AsyncFilterArgBinder @Nullable [] asyncArgBinders,
                       @Nullable
                       Predicate<FilterMethodContext> filterCondition,
                       @Nullable
                       ContinuationCreator continuationCreator,
                       boolean filtersException,
                       FilterReturnHandler returnHandler,
                       boolean isConditional,
                       @Nullable Executor executor,
                       boolean isReactive) implements InternalHttpFilter {

    private static final Predicate<FilterMethodContext> FILTER_CONDITION_ALWAYS_TRUE = runner -> true;
    private static final String RESPONSE_MISSING_MESSAGE = "Http response is missing";
    /**
     * Marks an empty reactive, asynchronous or flow result.
     */
    private static final Object EMPTY_RESULT = new Object();

    /**
     * Map an empty value to {@link #EMPTY_RESULT}, as the flow operators skip an empty value.
     *
     * @param flow The flow
     * @return The flow with the empty value mapped
     */
    private static ExecutionFlow<Object> withEmptyResult(ExecutionFlow<?> flow) {
        if (flow instanceof ReactiveExecutionFlow<?> reactiveFlow) {
            return ReactiveExecutionFlow.fromPublisher(
                Mono.<Object>from(reactiveFlow.toPublisher()).defaultIfEmpty(EMPTY_RESULT)
            );
        }
        // the imperative map is applied to an empty value as well
        return flow.map(v -> v == null ? EMPTY_RESULT : v);
    }

    static <T> MethodFilter<T> prepareFilterMethod(ConversionService conversionService,
                                                   T bean,
                                                   ExecutableMethod<T, ?> method,
                                                   boolean isResponseFilter,
                                                   FilterOrder order,
                                                   RequestBinderRegistry argumentBinderRegistry,
                                                   @Nullable Executor executor) throws IllegalArgumentException {
        return prepareFilterMethod(conversionService, bean, method, method.getArguments(), method.getReturnType().asArgument(), isResponseFilter, order, argumentBinderRegistry, executor);
    }

    static <T> MethodFilter<T> prepareFilterMethod(ConversionService conversionService,
                                                   T bean,
                                                   ExecutableMethod<T, ?> method,
                                                   Argument<?>[] arguments,
                                                   Argument<?> returnType,
                                                   boolean isResponseFilter,
                                                   FilterOrder order,
                                                   RequestBinderRegistry argumentBinderRegistry,
                                                   @Nullable Executor executor) throws IllegalArgumentException {

        FilterArgBinder[] fulfilled = new FilterArgBinder[arguments.length];
        AsyncFilterArgBinder[] asyncArgBinders = null;
        Predicate<FilterMethodContext> filterCondition = FILTER_CONDITION_ALWAYS_TRUE;
        boolean skipOnError = isResponseFilter;
        boolean filtersException = false;
        ContinuationCreator continuationCreator = null;
        boolean reactiveContinuation = false;
        for (int i = 0; i < arguments.length; i++) {
            Argument<?> argument = arguments[i];
            Class<?> argumentType = argument.getType();
            if (argumentType.isAssignableFrom(HttpRequest.class)) {
                fulfilled[i] = ctx -> ctx.request;
            } else if (argumentType.isAssignableFrom(ServerHttpRequest.class)) {
                // todo: only permit for server
                fulfilled[i] = ctx -> (ServerHttpRequest<?>) ctx.request;
            } else if (argumentType.isAssignableFrom(MutableHttpRequest.class)) {
                fulfilled[i] = ctx -> {
                    HttpRequest<?> request = ctx.request;
                    if (!(ctx.request instanceof MutableHttpRequest<?>)) {
                        request = ctx.request.mutate();
                    }
                    return request;
                };
            } else if (argumentType.isAssignableFrom(MutableHttpResponse.class)) {
                if (!isResponseFilter) {
                    throw new IllegalArgumentException("Filter is called before the response is known, can't have a response argument");
                }
                fulfilled[i] = ctx -> ctx.response;
            } else if (Throwable.class.isAssignableFrom(argumentType)) {
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
            } else if (argumentType == FilterContinuation.class) {
                if (isResponseFilter) {
                    throw new IllegalArgumentException("Response filters cannot use filter continuations");
                }
                if (continuationCreator != null) {
                    throw new IllegalArgumentException("Only one continuation per filter is allowed");
                }
                Argument<?> continuationReturnType = argument.getFirstTypeVariable().orElseThrow(() -> new IllegalArgumentException("Continuations must specify generic type"));
                if (isExecutionFlow(continuationReturnType) && continuationReturnType.getWrappedType().isAssignableFrom(MutableHttpResponse.class)) {
                    if (isExecutionFlow(returnType)) {
                        continuationCreator = ResultAwareExecutionFlowContinuationImpl::new;
                    } else {
                        continuationCreator = ExecutionFlowContinuationImpl::new;
                    }
                    fulfilled[i] = ctx -> ctx.continuation;
                } else if (isReactive(continuationReturnType) && continuationReturnType.getWrappedType().isAssignableFrom(MutableHttpResponse.class)) {
                    if (isReactive(returnType)) {
                        continuationCreator = ResultAwareReactiveContinuationImpl::new;
                    } else {
                        continuationCreator = ReactiveContinuationImpl::new;
                    }
                    reactiveContinuation = true;
                    fulfilled[i] = ctx -> ctx.continuation;
                } else if (continuationReturnType.getType().isAssignableFrom(MutableHttpResponse.class)) {
                    continuationCreator = BlockingContinuationImpl::new;
                    fulfilled[i] = ctx -> ctx.continuation;
                } else {
                    throw new IllegalArgumentException("Unsupported continuation type: " + continuationReturnType);
                }
            } else if (argumentType == MutablePropagatedContext.class) {
                fulfilled[i] = ctx -> ctx.mutablePropagatedContext;
            } else {
                ArgumentBinder<Object, HttpRequest<?>> argumentBinder = (ArgumentBinder<Object, HttpRequest<?>>) argumentBinderRegistry.findArgumentBinder(argument).orElse(null);
                if (argumentBinder != null) {
                    if (argumentBinder instanceof BaseFilterProcessor.AsyncBodyBinder<Object> async) {
                        if (isResponseFilter) {
                            throw new IllegalArgumentException("Cannot bind @Body in response filter method [" + method.getDescription(true) + "]");
                        }
                        if (asyncArgBinders == null) {
                            asyncArgBinders = new AsyncFilterArgBinder[arguments.length];
                        }
                        asyncArgBinders[i] = ctx -> {
                            HttpRequest<?> request = ctx.request;
                            ArgumentConversionContext<Object> conversionContext = (ArgumentConversionContext<Object>) ConversionContext.of(argument);
                            return async.bindAsync(conversionContext, request).map(result -> convertResult(method, argument, result));
                        };
                    } else {
                        fulfilled[i] = ctx -> {
                            HttpRequest<?> request = ctx.request;
                            ArgumentConversionContext<Object> conversionContext = (ArgumentConversionContext<Object>) ConversionContext.of(argument);
                            ArgumentBinder.BindingResult<Object> result = argumentBinder.bind(conversionContext, request);
                            return convertResult(method, argument, result);
                        };
                        if (argumentBinder instanceof FilterArgumentBinderPredicate pred) {
                            filterCondition = filterCondition.and(ctx -> pred.test(argument, ctx.mutablePropagatedContext, ctx.request, ctx.response, ctx.failure));
                        }
                    }
                } else {
                    throw new IllegalArgumentException("Unsupported filter argument type: " + argument);
                }
            }
        }
        if (skipOnError) {
            filterCondition = filterCondition.and(ctx -> ctx.failure == null);
        } else if (filterCondition == FILTER_CONDITION_ALWAYS_TRUE) {
            filterCondition = null;
        }
        FilterReturnHandler returnHandler = prepareReturnHandler(conversionService, returnType, isResponseFilter, continuationCreator != null, false);
        return new MethodFilter<>(
            order,
            bean,
            method,
            method instanceof UnsafeExecutable unsafeExecutable ? unsafeExecutable : null,
            isResponseFilter,
            fulfilled,
            asyncArgBinders,
            filterCondition,
            continuationCreator,
            filtersException,
            returnHandler,
            bean instanceof ConditionalFilter,
            executor,
            isReactive(returnType) || reactiveContinuation
        );
    }

    @Nullable
    private static <T> Object convertResult(ExecutableMethod<T, ?> method, Argument<?> argument, ArgumentBinder.BindingResult<Object> result) {
        if (result.isPresentAndSatisfied() || (argument.isNullable() && result.isSatisfied())) {
            return result.getValue().orElse(null);
        } else {
            List<ConversionError> conversionErrors = result.getConversionErrors();
            if (!conversionErrors.isEmpty()) {
                throw new ConversionErrorException(argument, conversionErrors.getFirst());
            } else {
                throw new IllegalArgumentException("Unbindable argument [" + argument + "] to method [" + method.getDescription(true) + "]");
            }
        }
    }

    private static boolean isReactive(Argument<?> continuationReturnType) {
        // Argument.isReactive doesn't work in http-validation, this is a workaround
        return continuationReturnType.isReactive() || continuationReturnType.getType() == Publisher.class;
    }

    private static boolean isExecutionFlow(Argument<?> type) {
        return ExecutionFlow.class.isAssignableFrom(type.getType());
    }

    @Override
    public boolean isEnabled(HttpRequest<?> request) {
        if (isConditional) {
            return ((ConditionalFilter) bean).isEnabled(request);
        }
        return true;
    }

    @Override
    public boolean isFiltersRequest() {
        return !isResponseFilter;
    }

    @Override
    public boolean isFiltersResponse() {
        return isResponseFilter;
    }

    @Override
    public boolean hasContinuation() {
        return continuationCreator != null;
    }

    @Override
    public ExecutionFlow<FilterContext> processRequestFilter(FilterContext context) {
        if (continuationCreator != null) {
            throw new IllegalStateException("Downstream callback is required for filters with a continuation");
        }
        FilterMethodContext filterMethodContext = new FilterMethodContext(
            MutablePropagatedContext.of(context.propagatedContext()),
            context.request(),
            context.response(),
            null,
            null);
        return filter(context, filterMethodContext, null, false);
    }

    @Override
    public ExecutionFlow<FilterContext> processRequestFilter(FilterContext context,
                                                             Function<FilterContext, ExecutionFlow<FilterContext>> downstream) {
        if (continuationCreator == null) {
            throw new IllegalStateException("Downstream method shouldn't be called when continuation is missing!");
        }
        MutablePropagatedContext mutablePropagatedContext = MutablePropagatedContext.of(context.propagatedContext());
        FilterMethodContext filterMethodContext = new FilterMethodContext(
            mutablePropagatedContext,
            context.request(),
            context.response(),
            null,
            createContinuation(downstream, context, mutablePropagatedContext));
        return filter(context, filterMethodContext, null, false);
    }

    @Override
    public ExecutionFlow<FilterContext> processResponseFilter(FilterContext context, @Nullable Throwable exceptionToFilter) {
        if (exceptionToFilter != null && !filtersException) {
            return ExecutionFlow.just(context);
        }
        if (continuationCreator != null) {
            return ExecutionFlow.error(new IllegalStateException("Response filter cannot have a continuation!"));
        }
        FilterMethodContext filterMethodContext = new FilterMethodContext(
            MutablePropagatedContext.of(context.propagatedContext()),
            context.request(),
            context.response(),
            exceptionToFilter,
            null);
        return filter(context, filterMethodContext, null, false);
    }

    @Override
    public int getOrder() {
        return order.getOrder(bean);
    }

    private InternalFilterContinuation<?> createContinuation(Function<FilterContext, ExecutionFlow<FilterContext>> downstream,
                                                             FilterContext filterContext,
                                                             MutablePropagatedContext mutablePropagatedContext) {
        return Objects.requireNonNull(continuationCreator, "Continuation creator is required").create(downstream, filterContext, mutablePropagatedContext);
    }

    private ExecutionFlow<FilterContext> filter(
        FilterContext filterContext,
        FilterMethodContext methodContext,
        Object @Nullable [] args,
        boolean onExecutor
    ) {
        // this is intentionally one method instead of three nested ones to reduce stacktrace depth
        // and avoid unnecessary propagate calls

        PropagatedContext propagatedContext = filterContext.propagatedContext();
        try {
            if (propagatedContext.isBound()) {
                return doFilter(filterContext, methodContext, args, onExecutor);
            }
            return propagatedContext.propagate(() -> doFilter(filterContext, methodContext, args, onExecutor));
        } catch (Throwable e) {
            return ExecutionFlow.error(e);
        }
    }

    private ExecutionFlow<FilterContext> doFilter(FilterContext filterContext, FilterMethodContext methodContext, Object @Nullable [] args, boolean onExecutor) {
        if (args == null) {
            if (filterCondition != null && !filterCondition.test(methodContext)) {
                return ExecutionFlow.just(filterContext);
            }
            if (asyncArgBinders != null) {
                ExecutionFlow<Object[]> argsFlow = bindArgsAsync(methodContext);
                if (filterContext.reactive()) {
                    // subscribe the downstream in the reactive chain to keep its Reactor context
                    argsFlow = ReactiveExecutionFlow.fromFlow(argsFlow);
                }
                return argsFlow.flatMap(a -> filter(filterContext, methodContext, a, onExecutor));
            } else {
                try {
                    args = bindArgsSync(methodContext);
                } catch (Throwable e) {
                    return ExecutionFlow.error(e);
                }
            }
        }
        if (!onExecutor && executor != null) {
            Object[] finalArgs = args;
            if (isReactive || filterContext.reactive()) {
                // a reactive flow keeps the Reactor context of the subscriber for the downstream filters and the route
                return ReactiveExecutionFlow.async(executor, () -> filter(filterContext, methodContext, finalArgs, true));
            }
            return ExecutionFlow.async(executor, () -> filter(filterContext, methodContext, finalArgs, true));
        }
        try {
            Object returnValue;
            if (unsafeExecutable != null) {
                returnValue = unsafeExecutable.invokeUnsafe(bean, args);
            } else {
                returnValue = Objects.requireNonNull(method).invoke(bean, args);
            }
            ExecutionFlow<FilterContext> executionFlow = returnHandler.handle(filterContext, returnValue, methodContext.continuation);
            MutablePropagatedContext mutablePropagatedContext = methodContext.mutablePropagatedContext;
            if (!(executionFlow instanceof ImperativeExecutionFlow<FilterContext>)) {
                // an asynchronous filter can change the context until its result completes
                return executionFlow.map(fc -> withMutatedContext(fc, filterContext.propagatedContext(), mutablePropagatedContext));
            }
            PropagatedContext mutatedPropagatedContext = mutablePropagatedContext.getContext();
            if (mutatedPropagatedContext != filterContext.propagatedContext() && mutatedPropagatedContext != null) {
                executionFlow = executionFlow.map(fc -> fc.withPropagatedContext(mutatedPropagatedContext));
            }
            return executionFlow;
        } catch (Throwable e) {
            return ExecutionFlow.error(e);
        }
    }

    private static FilterContext withMutatedContext(FilterContext filterContext,
                                                    PropagatedContext propagatedContext,
                                                    MutablePropagatedContext mutablePropagatedContext) {
        PropagatedContext mutatedPropagatedContext = mutablePropagatedContext.getContext();
        if (mutatedPropagatedContext != propagatedContext && mutatedPropagatedContext != null) {
            return filterContext.withPropagatedContext(mutatedPropagatedContext);
        }
        return filterContext;
    }

    private Object[] bindArgsSync(FilterMethodContext context) {
        Object[] args = new Object[argBinders.length];
        for (int i = 0; i < args.length; i++) {
            FilterArgBinder binder = argBinders[i];
            if (binder != null) {
                args[i] = binder.bind(context);
            }
        }
        return args;
    }

    private ExecutionFlow<Object[]> bindArgsAsync(FilterMethodContext context) {
        Objects.requireNonNull(asyncArgBinders);
        Object[] args;
        try {
            args = bindArgsSync(context);
        } catch (Throwable e) {
            return ExecutionFlow.error(e);
        }
        ExecutionFlow<Object[]> result = ExecutionFlow.just(args);
        for (int i = 0; i < asyncArgBinders.length; i++) {
            AsyncFilterArgBinder binder = asyncArgBinders[i];
            if (binder != null) {
                int position = i;
                result = result.flatMap(a -> binder.bind(context).map(arg -> {
                    args[position] = arg;
                    return args;
                }));
            }
        }
        return result.map(o -> args);
    }

    @SuppressWarnings({"java:S3776", "java:S3740"}) // performance
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
                if (nullable) {
                    return FilterReturnHandler.FROM_REQUEST_RESPONSE_NULLABLE;
                } else {
                    return FilterReturnHandler.FROM_REQUEST_RESPONSE;
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
        if (isExecutionFlow(type)) {
            var next = prepareReturnHandler(conversionService, type.getWrappedType(), isResponseFilter, hasContinuation, false);
            return (context, returnValue, continuation) -> {
                if (returnValue == null) {
                    if (!nullable) {
                        return ExecutionFlow.error(new NullPointerException("Returned flow must not be null, or mark the method as @Nullable"));
                    }
                    return ExecutionFlow.just(context);
                }
                ExecutionFlow<?> flow = (ExecutionFlow<?>) returnValue;
                if (flow instanceof ReactiveExecutionFlow<?> reactiveFlow) {
                    // the same propagation as a returned publisher
                    flow = ReactiveExecutionFlow.fromPublisher(
                        ReactivePropagation.propagate(context.propagatedContext(), reactiveFlow.toPublisher())
                    );
                }
                if (continuation instanceof ResultAwareContinuation resultAwareContinuation) {
                    return resultAwareContinuation.processResult(flow);
                }
                // flatMap skips an empty value, an empty flow proceeds with the current context
                return withEmptyResult(flow)
                    .flatMap(v -> v == EMPTY_RESULT ? ExecutionFlow.just(context) : next.handle(context, v, continuation));
            };
        } else if (isReactive(type)) {
            var next = prepareReturnHandler(conversionService, type.getWrappedType(), isResponseFilter, hasContinuation, false);
            return (context, returnValue, continuation) -> {
                if (returnValue == null && !nullable) {
                    return ExecutionFlow.error(new NullPointerException("Returned publisher must not be null, or mark the method as @Nullable"));
                }
                Publisher<Object> converted = Publishers.convertToPublisher(conversionService, returnValue == null ? Mono.empty() : returnValue);
                if (continuation instanceof ResultAwareContinuation resultAwareContinuation) {
                    return resultAwareContinuation.processResult(converted);
                }
                // flatMap skips an empty value, an empty publisher proceeds with the current context
                return ReactiveExecutionFlow.fromPublisherEager(converted, context.propagatedContext())
                    .map(v -> v == null ? EMPTY_RESULT : v)
                    .flatMap(v -> v == EMPTY_RESULT ? ExecutionFlow.just(context) : next.handle(context, v, continuation));
            };
        } else if (type.isAsync()) {
            var next = prepareReturnHandler(conversionService, type.getWrappedType(), isResponseFilter, hasContinuation, false);
            return new DelayedFilterReturnHandler(isResponseFilter, next, nullable) {
                @Override
                protected ExecutionFlow<?> toFlow(FilterContext context, Object returnValue, @Nullable InternalFilterContinuation<?> continuation) {
                    //noinspection unchecked
                    return CompletableFutureExecutionFlow.just(((CompletionStage<Object>) returnValue).toCompletableFuture());
                }
            };
        } else {
            throw new IllegalArgumentException("Unsupported filter return type " + type.getType().getName());
        }
    }

    private record FilterMethodContext(
        MutablePropagatedContext mutablePropagatedContext,
        HttpRequest<?> request,
        @Nullable HttpResponse<?> response,
        @Nullable Throwable failure,
        @Nullable InternalFilterContinuation<?> continuation) {
    }

    private interface FilterArgBinder {
        @Nullable
        Object bind(FilterMethodContext context);
    }

    private interface AsyncFilterArgBinder {
        ExecutionFlow<Object> bind(FilterMethodContext context);
    }

    /**
     * The continuation creator.
     */
    private interface ContinuationCreator {

        InternalFilterContinuation<?> create(Function<FilterContext, ExecutionFlow<FilterContext>> downstream,
                                             FilterContext filterContext,
                                             MutablePropagatedContext mutablePropagatedContext);

    }

    private interface FilterReturnHandler {
        /**
         * Void method that accepts a continuation.
         */
        FilterReturnHandler VOID_WITH_CONTINUATION = (filterContext, returnValue, continuation) -> ExecutionFlow.just(Objects.requireNonNull(continuation, "Continuation is required").afterMethodContext());
        /**
         * Void method.
         */
        FilterReturnHandler VOID = (filterContext, returnValue, continuation) -> ExecutionFlow.just(filterContext);
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

        @SuppressWarnings("java:S112")
        // internal interface
        ExecutionFlow<FilterContext> handle(FilterContext context,
                                            @Nullable Object returnValue,
                                            @Nullable InternalFilterContinuation<?> passedOnContinuation);
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

        @SuppressWarnings("java:S1452")
        protected abstract ExecutionFlow<?> toFlow(FilterContext context,
                                                   Object returnValue,
                                                   @Nullable InternalFilterContinuation<?> continuation);

        @Override
        public ExecutionFlow<FilterContext> handle(FilterContext context,
                                                   @Nullable Object returnValue,
                                                   @Nullable InternalFilterContinuation<?> continuation) {
            try {
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
                        return ExecutionFlow.error(doneFlow.getError());
                    }
                    return next.handle(context, doneFlow.getValue(), continuation);
                } else {
                    // flatMap skips an empty value, a stage completed with null is handled like a returned null
                    return withEmptyResult(delayedFlow).flatMap(v -> {
                        try {
                            return next.handle(context, v == EMPTY_RESULT ? null : v, continuation);
                        } catch (Throwable e) {
                            return ExecutionFlow.error(e);
                        }
                    });
                }
            } catch (Throwable e) {
                return ExecutionFlow.error(e);
            }
        }
    }

    /**
     * The internal filter continuation implementation.
     *
     * @param <R> The response type
     */
    private sealed interface InternalFilterContinuation<R> extends FilterContinuation<R> {

        FilterContext afterMethodContext();
    }

    /**
     * The reactive continuation that processes the method return value.
     */
    private static final class ResultAwareReactiveContinuationImpl extends ReactiveContinuationImpl
        implements ResultAwareContinuation<Publisher<HttpResponse<?>>> {

        private ResultAwareReactiveContinuationImpl(Function<FilterContext, ExecutionFlow<FilterContext>> next,
                                                    FilterContext filterContext,
                                                    MutablePropagatedContext mutablePropagatedContext) {
            super(next, filterContext, mutablePropagatedContext);
        }

        @Override
        public ExecutionFlow<FilterContext> processResult(Publisher<HttpResponse<?>> publisher) {
            // an empty publisher proceeds with the context after the continuation, the downstream response if it was called
            ExecutionFlow<HttpResponse<?>> immediate = ReactiveExecutionFlow.fromPublisherImmediate(publisher);
            if (immediate != null) {
                // Mono.just, Mono.error or the continuation publisher itself: no Reactor chain is needed
                return immediate.map(httpResponse -> httpResponse == null ? filterContext : filterContext.withResponse(httpResponse));
            }
            // a lazy publisher keeps the chain reactive: the Reactor context of an upstream filter has to reach it
            Mono<HttpResponse<?>> mono = Mono.from(ReactivePropagation.propagate(filterContext.propagatedContext(), publisher));
            return ReactiveExecutionFlow.fromPublisher(
                mono
                    .map(httpResponse -> filterContext.withResponse(httpResponse))
                    .switchIfEmpty(Mono.fromSupplier(() -> filterContext))
            );
        }
    }

    /**
     * The execution flow continuation that processes the method return value.
     */
    private static final class ResultAwareExecutionFlowContinuationImpl extends ExecutionFlowContinuationImpl
        implements ResultAwareContinuation<ExecutionFlow<HttpResponse<?>>> {

        private ResultAwareExecutionFlowContinuationImpl(Function<FilterContext, ExecutionFlow<FilterContext>> downstream,
                                                         FilterContext filterContext,
                                                         MutablePropagatedContext mutablePropagatedContext) {
            super(downstream, filterContext, mutablePropagatedContext);
        }

        @Override
        public ExecutionFlow<FilterContext> processResult(ExecutionFlow<HttpResponse<?>> flow) {
            // an empty flow proceeds with the context after the continuation, the downstream response if it was called
            return withEmptyResult(flow)
                .map(httpResponse -> httpResponse == EMPTY_RESULT ? filterContext : filterContext.withResponse((HttpResponse<?>) httpResponse));
        }
    }

    /**
     * Continuation implementation that yields an {@link ExecutionFlow}. The downstream flow is
     * returned as is, so a reactive downstream stays reactive and keeps the Reactor context.
     */
    private static sealed class ExecutionFlowContinuationImpl implements FilterContinuation<ExecutionFlow<HttpResponse<?>>>,
        InternalFilterContinuation<ExecutionFlow<HttpResponse<?>>> {

        protected FilterContext filterContext;
        private final Function<FilterContext, ExecutionFlow<FilterContext>> downstream;
        private final MutablePropagatedContext mutablePropagatedContext;

        private ExecutionFlowContinuationImpl(Function<FilterContext, ExecutionFlow<FilterContext>> downstream,
                                              FilterContext filterContext,
                                              MutablePropagatedContext mutablePropagatedContext) {
            this.downstream = downstream;
            this.filterContext = filterContext;
            this.mutablePropagatedContext = mutablePropagatedContext;
        }

        @Override
        public FilterContinuation<ExecutionFlow<HttpResponse<?>>> request(HttpRequest<?> request) {
            filterContext = filterContext.withRequest(request);
            return this;
        }

        @Override
        public ExecutionFlow<HttpResponse<?>> proceed() {
            PropagatedContext propagatedContext = filterContext.propagatedContext();
            PropagatedContext mutatedPropagatedContext = mutablePropagatedContext.getContext();
            if (propagatedContext != mutatedPropagatedContext && mutatedPropagatedContext != null) {
                filterContext = filterContext.withPropagatedContext(mutatedPropagatedContext);
            } else {
                filterContext = filterContext.withPropagatedContext(PropagatedContext.find().orElse(filterContext.propagatedContext()));
            }
            // the downstream is called on the first use of the flow, reactively if it's converted to a publisher
            return new SubscriberAwareExecutionFlow<>() {
                @Override
                protected ExecutionFlow<HttpResponse<?>> create(boolean reactive) {
                    if (reactive) {
                        filterContext = filterContext.asReactive();
                    }
                    ExecutionFlow<FilterContext> downstreamFlow;
                    try {
                        downstreamFlow = downstream.apply(filterContext);
                    } catch (Exception e) {
                        return ExecutionFlow.error(e);
                    }
                    return downstreamFlow.map(newFilterContext -> {
                        filterContext = newFilterContext;
                        return Objects.requireNonNull(newFilterContext.response(), RESPONSE_MISSING_MESSAGE);
                    });
                }
            };
        }

        @Override
        public FilterContext afterMethodContext() {
            return filterContext;
        }
    }

    /**
     * Continuation implementation that yields a reactive type.<br>
     * This class implements a bunch of interfaces that it would otherwise have to create lambdas
     * for.
     */
    private static sealed class ReactiveContinuationImpl implements FilterContinuation<Publisher<HttpResponse<?>>>,
        InternalFilterContinuation<Publisher<HttpResponse<?>>> {

        protected FilterContext filterContext;
        private final Function<FilterContext, ExecutionFlow<FilterContext>> downstream;
        private final MutablePropagatedContext mutablePropagatedContext;

        private ReactiveContinuationImpl(Function<FilterContext, ExecutionFlow<FilterContext>> downstream,
                                         FilterContext filterContext,
                                         MutablePropagatedContext mutablePropagatedContext) {
            this.downstream = downstream;
            this.filterContext = filterContext;
            this.mutablePropagatedContext = mutablePropagatedContext;
        }

        @Override
        public FilterContinuation<Publisher<HttpResponse<?>>> request(HttpRequest<?> request) {
            // keep this continuation, the method result is processed with its context
            filterContext = filterContext.withRequest(request);
            return this;
        }

        @Override
        public Publisher<HttpResponse<?>> proceed() {
            PropagatedContext propagatedContext = filterContext.propagatedContext();
            PropagatedContext mutatedPropagatedContext = mutablePropagatedContext.getContext();
            if (propagatedContext != mutatedPropagatedContext && mutatedPropagatedContext != null) {
                filterContext = filterContext.withPropagatedContext(mutatedPropagatedContext);
            } else {
                filterContext = filterContext.withPropagatedContext(PropagatedContext.find().orElse(filterContext.propagatedContext()));
            }
            filterContext = filterContext.asReactive();
            return ReactiveExecutionFlow.toPublisher(
                downstream.apply(filterContext).<HttpResponse<?>>map(newFilterContext -> {
                    filterContext = newFilterContext;
                    return Objects.requireNonNull(newFilterContext.response(), RESPONSE_MISSING_MESSAGE);
                })
            );
        }

        @Override
        public FilterContext afterMethodContext() {
            return filterContext;
        }
    }

    /**
     * The internal continuation that processes the method result.
     *
     * @param <T> The continuation result.
     */
    private sealed interface ResultAwareContinuation<T> extends InternalFilterContinuation<T> {

        ExecutionFlow<FilterContext> processResult(T result);

    }

    /**
     * Implementation of {@link FilterContinuation} for blocking calls.
     */
    @SuppressWarnings("java:S112") // framework code
    private static final class BlockingContinuationImpl implements FilterContinuation<HttpResponse<?>>, InternalFilterContinuation<HttpResponse<?>> {

        private final Function<FilterContext, ExecutionFlow<FilterContext>> downstream;
        private FilterContext filterContext;
        private final MutablePropagatedContext mutablePropagatedContext;

        private BlockingContinuationImpl(Function<FilterContext, ExecutionFlow<FilterContext>> downstream,
                                         FilterContext filterContext,
                                         MutablePropagatedContext mutablePropagatedContext) {
            this.downstream = downstream;
            this.filterContext = filterContext;
            this.mutablePropagatedContext = mutablePropagatedContext;
        }

        @Override
        public FilterContinuation<HttpResponse<?>> request(HttpRequest<?> request) {
            filterContext = filterContext.withRequest(request);
            PropagatedContext propagatedContext = filterContext.propagatedContext();
            PropagatedContext mutatedPropagatedContext = mutablePropagatedContext.getContext();
            if (propagatedContext != mutatedPropagatedContext && mutatedPropagatedContext != null) {
                filterContext = filterContext.withPropagatedContext(mutatedPropagatedContext);
            } else {
                filterContext = filterContext.withPropagatedContext(PropagatedContext.find().orElse(filterContext.propagatedContext()));
            }
            return new BlockingContinuationImpl(downstream, filterContext, mutablePropagatedContext);
        }

        @Override
        public HttpResponse<?> proceed() {
            if (Thread.currentThread() instanceof NonBlocking) {
                throw new IllegalStateException("Cannot use blocking continuation on non-blocking thread. Please mark the filter to run on another thread with @ExecuteOn, or use a reactive continuation.");
            }

            boolean interrupted = false;
            while (true) {
                try {
                    filterContext = downstream.apply(filterContext).toCompletableFuture().get();
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    return Objects.requireNonNull(filterContext.response(), RESPONSE_MISSING_MESSAGE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
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

        @Override
        public FilterContext afterMethodContext() {
            return filterContext;
        }
    }

}
