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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
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
import io.micronaut.inject.ExecutableMethod;
import org.reactivestreams.Publisher;
import reactor.core.scheduler.NonBlocking;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Internal implementation of {@link io.micronaut.http.annotation.ServerFilter}.
 *
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
 * @param <T>                 The bean type
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
                       @Nullable FilterArgBinder @NonNull [] argBinders,
                       @Nullable AsyncFilterArgBinder @Nullable [] asyncArgBinders,
                       @Nullable
                       Predicate<FilterMethodContext> filterCondition,
                       @Nullable
                       ContinuationCreator continuationCreator,
                       boolean filtersException,
                       FilterReturnHandler returnHandler,
                       boolean isConditional
) implements InternalHttpFilter {

    private static final Predicate<FilterMethodContext> FILTER_CONDITION_ALWAYS_TRUE = runner -> true;

    static <T> MethodFilter<T> prepareFilterMethod(ConversionService conversionService,
                                                   T bean,
                                                   ExecutableMethod<T, ?> method,
                                                   boolean isResponseFilter,
                                                   FilterOrder order,
                                                   RequestBinderRegistry argumentBinderRegistry) throws IllegalArgumentException {
        return prepareFilterMethod(conversionService, bean, method, method.getArguments(), method.getReturnType().asArgument(), isResponseFilter, order, argumentBinderRegistry);
    }

    static <T> MethodFilter<T> prepareFilterMethod(ConversionService conversionService,
                                                   @Nullable T bean,
                                                   @Nullable ExecutableMethod<T, ?> method,
                                                   Argument<?>[] arguments,
                                                   Argument<?> returnType,
                                                   boolean isResponseFilter,
                                                   FilterOrder order,
                                                   RequestBinderRegistry argumentBinderRegistry) throws IllegalArgumentException {

        FilterArgBinder[] fulfilled = new FilterArgBinder[arguments.length];
        AsyncFilterArgBinder[] asyncArgBinders = null;
        Predicate<FilterMethodContext> filterCondition = FILTER_CONDITION_ALWAYS_TRUE;
        boolean skipOnError = isResponseFilter;
        boolean filtersException = false;
        ContinuationCreator continuationCreator = null;
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
                if (isReactive(continuationReturnType) && continuationReturnType.getWrappedType().isAssignableFrom(MutableHttpResponse.class)) {
                    if (isReactive(returnType)) {
                        continuationCreator = ResultAwareReactiveContinuationImpl::new;
                    } else {
                        continuationCreator = ReactiveContinuationImpl::new;
                    }
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
            bean instanceof ConditionalFilter
        );
    }

    private static <T> Object convertResult(@Nullable ExecutableMethod<T, ?> method, Argument<?> argument, ArgumentBinder.BindingResult<Object> result) {
        if (result.isPresentAndSatisfied() || (argument.isNullable() && result.isSatisfied())) {
            return result.getValue().orElse(null);
        } else {
            List<ConversionError> conversionErrors = result.getConversionErrors();
            if (!conversionErrors.isEmpty()) {
                throw new ConversionErrorException(argument, conversionErrors.get(0));
            } else {
                throw new IllegalArgumentException("Unbindable argument [" + argument + "] to method [" + method.getDescription(true) + "]");
            }
        }
    }

    private static boolean isReactive(Argument<?> continuationReturnType) {
        // Argument.isReactive doesn't work in http-validation, this is a workaround
        return continuationReturnType.isReactive() || continuationReturnType.getType() == Publisher.class;
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
        return filter(context, filterMethodContext);
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
        return filter(context, filterMethodContext);
    }

    @Override
    public ExecutionFlow<FilterContext> processResponseFilter(FilterContext context, Throwable exceptionToFilter) {
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
        return filter(context, filterMethodContext);
    }

    @Override
    public int getOrder() {
        return order.getOrder(bean);
    }

    private InternalFilterContinuation<?> createContinuation(Function<FilterContext, ExecutionFlow<FilterContext>> downstream,
                                                             FilterContext filterContext,
                                                             MutablePropagatedContext mutablePropagatedContext) {
        return continuationCreator.create(downstream, filterContext, mutablePropagatedContext);
    }

    private ExecutionFlow<FilterContext> filter(FilterContext filterContext,
                                                FilterMethodContext methodContext) {
        try (PropagatedContext.Scope ignore = filterContext.propagatedContext().propagate()) {
            if (filterCondition != null && !filterCondition.test(methodContext)) {
                return ExecutionFlow.just(filterContext);
            }
            if (asyncArgBinders != null) {
                return bindArgsAsync(methodContext).flatMap(args -> filter0(filterContext, methodContext, args));
            } else {
                Object[] args;
                try {
                    args = bindArgsSync(methodContext);
                } catch (Throwable e) {
                    return ExecutionFlow.error(e);
                }
                return filter0(filterContext, methodContext, args);
            }
        } catch (Throwable e) {
            return ExecutionFlow.error(e);
        }
    }

    private ExecutionFlow<FilterContext> filter0(FilterContext filterContext, FilterMethodContext methodContext, Object[] args) {
        try {
            Object returnValue;
            if (unsafeExecutable != null) {
                returnValue = unsafeExecutable.invokeUnsafe(bean, args);
            } else {
                returnValue = method.invoke(bean, args);
            }
            ExecutionFlow<FilterContext> executionFlow = returnHandler.handle(filterContext, returnValue, methodContext.continuation);
            PropagatedContext mutatedPropagatedContext = methodContext.mutablePropagatedContext.getContext();
            if (mutatedPropagatedContext != filterContext.propagatedContext()) {
                executionFlow = executionFlow.map(fc -> fc.withPropagatedContext(mutatedPropagatedContext));
            }
            return executionFlow;
        } catch (Throwable e) {
            return ExecutionFlow.error(e);
        }
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
        assert asyncArgBinders != null;
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
        if (isReactive(type)) {
            var next = prepareReturnHandler(conversionService, type.getWrappedType(), isResponseFilter, hasContinuation, false);
            return (context, returnValue, continuation) -> {
                if (returnValue == null && !nullable) {
                    return next.handle(context, null, continuation);
                }
                Publisher<Object> converted = Publishers.convertToPublisher(conversionService, returnValue);
                if (continuation instanceof ResultAwareContinuation resultAwareContinuation) {
                    return resultAwareContinuation.processResult(ReactivePropagation.propagate(
                        context.propagatedContext(),
                        converted
                    ));
                }
                return ReactiveExecutionFlow.fromPublisherEager(converted, context.propagatedContext()).flatMap(v -> {
                    try {
                        return next.handle(context, v, continuation);
                    } catch (Throwable e) {
                        return ExecutionFlow.error(e);
                    }
                });
            };
        } else if (type.isAsync()) {
            var next = prepareReturnHandler(conversionService, type.getWrappedType(), isResponseFilter, hasContinuation, false);
            return new DelayedFilterReturnHandler(isResponseFilter, next, nullable) {
                @Override
                protected ExecutionFlow<?> toFlow(FilterContext context, Object returnValue, InternalFilterContinuation<?> continuation) {
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
        FilterReturnHandler VOID_WITH_CONTINUATION = (filterContext, returnValue, continuation) -> ExecutionFlow.just(continuation.afterMethodContext());
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
                                            @Nullable InternalFilterContinuation<?> passedOnContinuation) throws Throwable;
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
                                                   InternalFilterContinuation<?> continuation) throws Throwable {
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
            return ReactiveExecutionFlow.fromPublisher(publisher).map(httpResponse -> filterContext.withResponse(httpResponse));
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
            return new ReactiveContinuationImpl(downstream, filterContext.withRequest(request), mutablePropagatedContext);
        }

        @Override
        public Publisher<HttpResponse<?>> proceed() {
            PropagatedContext propagatedContext = filterContext.propagatedContext();
            PropagatedContext mutatedPropagatedContext = mutablePropagatedContext.getContext();
            if (propagatedContext != mutatedPropagatedContext) {
                filterContext = filterContext.withPropagatedContext(mutatedPropagatedContext);
            } else {
                filterContext = filterContext.withPropagatedContext(PropagatedContext.find().orElse(filterContext.propagatedContext()));
            }
            return ReactiveExecutionFlow.fromFlow(
                downstream.apply(filterContext).<HttpResponse<?>>map(newFilterContext -> {
                    filterContext = newFilterContext;
                    return newFilterContext.response();
                })
            ).toPublisher();
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
            if (propagatedContext != mutatedPropagatedContext) {
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
                    return filterContext.response();
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
