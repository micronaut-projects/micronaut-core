package io.micronaut.http.filter;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.execution.CompletableFutureExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.execution.ImperativeExecutionFlow;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

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

    private static final Object[] SKIP_FILTER = new Object[0];

    private final List<GenericHttpFilter> filters;

    private HttpRequest<?> request;
    private HttpResponse<?> response;
    private Throwable failure;
    private SuspensionPoint responseSuspensionPoint = new SuspensionPoint(-1, null);
    private int index;
    private boolean responseNeedsProcessing = false;

    private Context reactorContext = Context.empty();

    public FilterRunner(List<GenericHttpFilter> filters) {
        this.filters = filters;
    }

    private static void checkOrdered(List<GenericHttpFilter> filters) {
        if (!filters.stream().allMatch(f -> f instanceof Ordered)) {
            throw new IllegalStateException("Some filters cannot be ordered: " + filters);
        }
    }

    public static void sort(List<GenericHttpFilter> filters) {
        checkOrdered(filters);
        OrderUtil.sort(filters);
    }

    public static void sortReverse(List<GenericHttpFilter> filters) {
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
        ImperativeExecutionFlow<? extends HttpResponse<?>> done = flow.asComplete();
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

        if (filter instanceof GenericHttpFilter.Before<?> before) {
            if (executeOn == null) {
                // possibly continue with next filter
                return invokeBefore(before, null);
            } else {
                executeOn.execute(() -> {
                    if (invokeBefore(before, executeOn)) {
                        workRequest();
                    }
                });
                return false;
            }
        } else if (filter instanceof GenericHttpFilter.After<?>) {
            // skip filter, only used for response
            return true;
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

            if (filter instanceof GenericHttpFilter.After<?> after) {
                if (executeOn == null) {
                    if (!invokeAfter(after)) {
                        // suspend
                        return;
                    }
                } else {
                    executeOn.execute(() -> {
                        if (invokeAfter(after)) {
                            workResponse();
                        }
                    });
                    // suspend
                    return;
                }
            }
        }
    }

    private <T> boolean invokeBefore(GenericHttpFilter.Before<T> before, @Nullable Executor completeOn) {
        FilterContinuationImpl<?> passedOnContinuation = null;
        try {
            var oldSuspensionPoint = this.responseSuspensionPoint;
            Object[] args = satisfy(before.method().getArguments(), false);
            if (args == SKIP_FILTER) {
                // we don't technically need to reset this, but I can guarantee I will forget this
                // if satisfy() ever changes to return SKIP_FILTER for request handlers too
                this.responseSuspensionPoint = oldSuspensionPoint;
                return true;
            }
            SuspensionPoint newSuspensionPoint = this.responseSuspensionPoint;
            if (newSuspensionPoint != oldSuspensionPoint) {
                passedOnContinuation = (FilterContinuationImpl<?>) newSuspensionPoint;
                if (completeOn != null) {
                    passedOnContinuation.completeOn = completeOn;
                }
            }
            Object returnValue = before.method().invoke(before.bean(), args);

            boolean proceed = handleFilterReturn(returnValue, true, passedOnContinuation);
            if (passedOnContinuation != null && proceed) {
                throw new AssertionError("handleFilterReturn should never return true if there is a continuation");
            }
            return proceed;
        } catch (Throwable e) {
            if (passedOnContinuation == null) {
                failure = e;
                responseNeedsProcessing = true;
                workResponse();
            } else {
                passedOnContinuation.forwardResponse(null, e);
            }
            return false;
        }
    }

    private <T> boolean invokeAfter(GenericHttpFilter.After<T> after) {
        try {
            Object[] args = satisfy(after.method().getArguments(), true);
            if (args == SKIP_FILTER) {
                return true;
            }
            Object returnValue = after.method().invoke(after.bean(), args);
            return handleFilterReturn(returnValue, true, null);
        } catch (Throwable e) {
            failure = e;
            responseNeedsProcessing = true;
        }
        return true;
    }

    /**
     * @param requestFilter        Whether this is a request filter
     * @param passedOnContinuation If this is a request filter, this parameter can contain the
     *                             continuation we passed on to the filter.
     * @return {@code true} if we can continue with the next filter, {@code false} if we should
     * suspend
     */
    private boolean handleFilterReturn(Object returnValue, boolean requestFilter, FilterContinuationImpl<?> passedOnContinuation) throws Throwable {
        if (returnValue instanceof Optional<?> opt) {
            returnValue = opt.orElse(null);
        }
        if (returnValue == null) {
            if (passedOnContinuation != null) {
                // use response/failure from other filters
                passedOnContinuation.forwardResponse(response, failure);
                return false;
            } else {
                return true;
            }
        }
        if (requestFilter) {
            if (returnValue instanceof HttpRequest<?> req) {
                if (passedOnContinuation != null) {
                    throw new IllegalStateException("Filter method that accepts a continuation cannot return an HttpRequest");
                }
                this.request = req;
                return true;
            } else if (returnValue instanceof HttpResponse<?> resp) {
                if (passedOnContinuation != null) {
                    passedOnContinuation.forwardResponse(resp, null);
                } else {
                    // cancel request pipeline, move immediately to response handling
                    this.response = resp;
                    this.failure = null;
                    this.responseNeedsProcessing = true;
                    workResponse();
                }
                return false;
            }
        } else {
            if (passedOnContinuation != null) {
                throw new AssertionError();
            }
            if (returnValue instanceof HttpResponse<?> resp) {
                // cancel request pipeline, move immediately to response handling
                this.response = resp;
                this.failure = null;
                this.responseNeedsProcessing = true;
                return true;
            }
        }
        ExecutionFlow<?> delayedFlow;
        if (Publishers.isConvertibleToPublisher(returnValue)) {
            //noinspection unchecked
            delayedFlow = ReactiveExecutionFlow.fromPublisher(
                Mono.from(Publishers.convertPublisher(returnValue, Publisher.class))
                    .contextWrite(reactorContext));
        } else if (returnValue instanceof CompletionStage<?> cs) {
            delayedFlow = CompletableFutureExecutionFlow.just(cs.toCompletableFuture());
        } else {
            throw new UnsupportedOperationException("Unsupported filter return type " + returnValue.getClass().getName());
        }
        ImperativeExecutionFlow<?> doneFlow = delayedFlow.asComplete();
        if (doneFlow != null) {
            if (doneFlow.getError() != null) {
                throw doneFlow.getError();
            }
            return handleFilterReturn(doneFlow.getValue(), requestFilter, passedOnContinuation);
        } else {
            // suspend until flow completes
            delayedFlow.onComplete((v, e) -> {
                if (e == null) {
                    try {
                        if (handleFilterReturn(v, requestFilter, passedOnContinuation)) {
                            if (requestFilter) {
                                workRequest();
                            } else {
                                workResponse();
                            }
                        }
                        return;
                    } catch (Throwable t) {
                        e = t;
                    }
                }
                if (passedOnContinuation == null) {
                    failure = e;
                    responseNeedsProcessing = true;
                    workResponse();
                } else {
                    passedOnContinuation.forwardResponse(null, e);
                }
            });
            return false;
        }
    }

    private Object[] satisfy(
        Argument<?>[] arguments,
        boolean hasResponse
    ) throws Exception {
        Object[] fulfilled = new Object[arguments.length];
        // if there is a failure, only filters that can actually handle it should be called
        boolean skipBecauseUnhandledFailure = failure != null;
        boolean hasContinuation = false;
        for (int i = 0; i < arguments.length; i++) {
            Argument<?> argument = arguments[i];
            if (argument.getType().isAssignableFrom(MutableHttpRequest.class)) {
                fulfilled[i] = request;
            } else if (argument.getType().isAssignableFrom(MutableHttpResponse.class)) {
                if (!hasResponse) {
                    throw new IllegalStateException("Filter is called before the response is known, can't have a response argument");
                }
                fulfilled[i] = response;
            } else if (Throwable.class.isAssignableFrom(argument.getType())) {
                if (!hasResponse) {
                    throw new IllegalStateException("Request filters cannot handle exceptions");
                }
                if (failure != null && argument.isInstance(failure)) {
                    fulfilled[i] = failure;
                } else if (argument.isNullable()) {
                    fulfilled[i] = null;
                } else {
                    // can't fulfill this argument
                    return SKIP_FILTER;
                }
                skipBecauseUnhandledFailure = false;
            } else if (argument.getType() == FilterContinuation.class) {
                if (hasResponse) {
                    throw new IllegalStateException("Response filters cannot use filter continuations");
                }
                if (hasContinuation) {
                    throw new IllegalStateException("Only one continuation per filter is allowed");
                }
                Argument<?> continuationReturnType = argument.getFirstTypeVariable().orElseThrow(() -> new IllegalStateException("Continuations must specify generic type"));
                SuspensionPoint oldSuspensionPoint = this.responseSuspensionPoint;
                int ourIndex = this.index - 1;
                SuspensionPoint newSuspensionPoint;
                if (Publishers.isConvertibleToPublisher(continuationReturnType.getType())) {
                    newSuspensionPoint = new ReactiveContinuationImpl<>(ourIndex, oldSuspensionPoint, continuationReturnType.getType());
                } else if (continuationReturnType.getType().isAssignableFrom(MutableHttpResponse.class)) {
                    newSuspensionPoint = new BlockingContinuationImpl(ourIndex, oldSuspensionPoint);
                } else {
                    throw new IllegalStateException("Unsupported continuation type: " + continuationReturnType);
                }
                // invokeBefore will detect the new suspension point and handle it accordingly
                this.responseSuspensionPoint = newSuspensionPoint;
                fulfilled[i] = newSuspensionPoint;
                hasContinuation = true;
            } else {
                throw new IllegalStateException("Unsupported filter argument type: " + argument);
            }
        }
        if (skipBecauseUnhandledFailure) {
            return SKIP_FILTER;
        }
        return fulfilled;
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
        public R proceed(HttpRequest<?> request) {
            FilterRunner.this.request = request;
            return Publishers.convertPublisher(this, reactiveType);
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
            // HACK: kotlin coroutine context propagation only supports reactor types (see
            // ReactorContextInjector). If we want to support our own type, we would need our own
            // ContextInjector, but that interface is marked as internal.
            // Another solution could be to PR kotlin to support all CorePublishers in
            // ReactorContextInjector.
            return Mono.from(super.proceed(request));
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
        public HttpResponse<?> proceed(HttpRequest<?> request) {
            FilterRunner.this.request = request;
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
