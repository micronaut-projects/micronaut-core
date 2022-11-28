package io.micronaut.http.filter;

import io.micronaut.core.annotation.Internal;
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
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

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
 */
@Internal
public class FilterRunner {
    private static final Logger LOG = LoggerFactory.getLogger(FilterRunner.class);

    private static final Object[] SKIP_FILTER = new Object[0];

    private final List<InternalFilter> filters;

    private HttpRequest<?> request;
    private HttpResponse<?> response;
    private Throwable failure;
    private SuspensionPoint<HttpResponse<?>> responseSuspensionPoint = new SuspensionPoint<>(-1, null);
    private int index;
    private boolean responseNeedsProcessing = false;
    public FilterRunner(List<InternalFilter> filters) {
        this.filters = filters;
    }

    private static void checkOrdered(List<InternalFilter> filters) {
        if (!filters.stream().allMatch(f -> f instanceof Ordered)) {
            throw new IllegalStateException("Some filters cannot be ordered: " + filters);
        }
    }

    public static void sort(List<InternalFilter> filters) {
        checkOrdered(filters);
        OrderUtil.sort(filters);
    }

    public static void sortReverse(List<InternalFilter> filters) {
        checkOrdered(filters);
        OrderUtil.reverseSort(filters);
    }

    protected ExecutionFlow<? extends HttpResponse<?>> processResponse(HttpRequest<?> request, HttpResponse<?> response) {
        return ExecutionFlow.just(response);
    }

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
        ImperativeExecutionFlow<? extends HttpResponse<?>> done = flow.asDone();
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

    public final ExecutionFlow<MutableHttpResponse<?>> run(HttpRequest<?> request) {
        if (this.request != null) {
            throw new IllegalStateException("Can only process one request");
        }
        this.request = request;

        ExecutionFlow<HttpResponse<?>> resultFlow = CompletableFutureExecutionFlow.just(responseSuspensionPoint);
        workRequest();
        //noinspection unchecked
        return (ExecutionFlow) resultFlow;
    }

    private void workRequest() {
        ServerRequestContext.set(request);
        while (true) {
            if (!workRequestFilter(filters.get(index++))) {
                return;
            }
        }
    }

    private boolean workRequestFilter(InternalFilter filter) {
        if (filter instanceof InternalFilter.Before<?> before) {
            return invokeBefore(before);
            // continue with next filter
        } else if (filter instanceof InternalFilter.After<?>) {
            // skip filter, only used for response
            return true;
        } else if (filter instanceof InternalFilter.Async async) {
            if (async.actual() instanceof InternalFilter.After<?>) {
                // skip filter, only used for response
                return true;
            }
            async.executor().execute(() -> {
                if (workRequestFilter(async.actual())) {
                    workRequest();
                }
            });
            return false;
        } else if (filter instanceof InternalFilter.AroundLegacy around) {
            FilterChainImpl chainSuspensionPoint = new FilterChainImpl(index - 1, responseSuspensionPoint);
            responseSuspensionPoint = chainSuspensionPoint;
            try {
                Publisher<? extends HttpResponse<?>> result = around.bean().doFilter(request, chainSuspensionPoint);
                result.subscribe(chainSuspensionPoint);
            } catch (Throwable e) {
                chainSuspensionPoint.handleContinuationFilterException(e);
            }
            // suspend
            return false;
        } else if (filter instanceof InternalFilter.TerminalReactive || filter instanceof InternalFilter.Terminal) {
            ExecutionFlow<? extends HttpResponse<?>> terminalFlow;
            if (filter instanceof InternalFilter.Terminal t) {
                try {
                    terminalFlow = t.execute(request);
                } catch (Throwable e) {
                    terminalFlow = ExecutionFlow.error(e);
                }
            } else {
                terminalFlow = ReactiveExecutionFlow.fromPublisher(((InternalFilter.TerminalReactive) filter).responsePublisher());
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
        ServerRequestContext.set(request);
        while (true) {
            if (responseNeedsProcessing) {
                responseNeedsProcessing = false;
                if (!processResponse0()) {
                    // suspend
                    return;
                }
            }

            index--;

            if (responseSuspensionPoint.filterIndex == index) {
                SuspensionPoint<HttpResponse<?>> suspensionPoint = responseSuspensionPoint;
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
            InternalFilter filter = filters.get(index);
            if (filter instanceof InternalFilter.After<?> after) {
                if (!invokeAfter(after)) {
                    // suspend
                    return;
                }
            } else if (filter instanceof InternalFilter.Async async && async.actual() instanceof InternalFilter.After<?> after) {
                async.executor().execute(() -> {
                    if (invokeAfter(after)) {
                        workResponse();
                    }
                });
                // suspend
                return;
            }
        }
    }

    private <T> boolean invokeBefore(InternalFilter.Before<T> before) {
        // todo: handle ExecuteOn
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
            SuspensionPoint<HttpResponse<?>> newSuspensionPoint = this.responseSuspensionPoint;
            if (newSuspensionPoint != oldSuspensionPoint) {
                passedOnContinuation = (FilterContinuationImpl<?>) newSuspensionPoint;
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
                passedOnContinuation.handleContinuationFilterException(e);
            }
            return false;
        }
    }

    private <T> boolean invokeAfter(InternalFilter.After<T> after) {
        // todo: handle ExecuteOn
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
                Publishers.convertPublisher(returnValue, Publisher.class));
        } else if (returnValue instanceof CompletionStage<?> cs) {
            delayedFlow = CompletableFutureExecutionFlow.just(cs.toCompletableFuture());
        } else {
            throw new UnsupportedOperationException("Unsupported filter return type " + returnValue.getClass().getName());
        }
        ImperativeExecutionFlow<?> doneFlow = delayedFlow.asDone();
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
                    passedOnContinuation.handleContinuationFilterException(e);
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
                SuspensionPoint<HttpResponse<?>> oldSuspensionPoint = this.responseSuspensionPoint;
                int ourIndex = this.index - 1;
                SuspensionPoint<HttpResponse<?>> newSuspensionPoint;
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

    private static class SuspensionPoint<T> extends CompletableFuture<T> {
        final int filterIndex;
        @Nullable
        final SuspensionPoint<T> next;

        SuspensionPoint(int filterIndex, @Nullable SuspensionPoint<T> next) {
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
     * class has the {@link #decidedOnBranch} flag. It is set to {@link true} when the filter
     * calls {@link #proceed}, or when the filter throws an exception. Only the first of these
     * events actually continues working the {@link FilterRunner}, any events that follow are
     * discarded (logged or throw an exception).
     */
    private abstract class FilterContinuationImpl<R> extends SuspensionPoint<HttpResponse<?>> implements FilterContinuation<R> {
        final AtomicBoolean decidedOnBranch = new AtomicBoolean(false);
        final AtomicBoolean forwardedResponse = new AtomicBoolean(false);

        FilterContinuationImpl(int filterIndex, @Nullable SuspensionPoint<HttpResponse<?>> next) {
            super(filterIndex, next);
        }

        protected abstract R adapt();

        @Override
        public R proceed(HttpRequest<?> request) {
            FilterRunner.this.request = request;
            if (decidedOnBranch.compareAndSet(false, true)) {
                workRequest();
                return adapt();
            } else {
                throw new IllegalStateException("Already subscribed to proceed() publisher, or filter method threw an exception and was cancelled");
            }
        }

        void handleContinuationFilterException(Throwable e) {
            if (decidedOnBranch.compareAndSet(false, true)) {
                // proceed wasn't called, continue directly to response processing
                // cancel the suspension point, since we don't do more request processing
                responseSuspensionPoint = next;
                if (!forwardResponse(null, e)) {
                    // if another thread forwarded a response too, `responseSuspensionPoint` may have changed, and we may interfere! Should never happen.
                    LOG.warn("Potential race condition: Continuation failed early, but someone else also forwarded a response");
                }
            } else if (isDone()) {
                // proceed was called and the continuation was already done. The filter likely
                // already handled the response, and decided to error based on it.
                forwardResponse(null, e);
            } else {
                // proceed was called, need to wait for completion to avoid concurrency issues
                // filters should really not do this: Either throw the exception before the proceed call, or emit it from the returned publisher
                whenComplete((resp, err) -> {
                    if (err == null) {
                        LOG.warn("Filter method threw exception after chain.proceed() had already been called. This can lead to memory leaks, please fix your filter!", e);
                        forwardResponse(null, e);
                    } else {
                        LOG.warn("Filter method threw exception after chain.proceed() had already been called. Downstream handlers also threw an exception, that one is being forwarded.", e);
                        forwardResponse(null, err);
                    }
                });
            }
        }

        boolean forwardResponse(HttpResponse<?> response, Throwable failure) {
            if (!forwardedResponse.compareAndSet(false, true)) {
                if (failure == null) {
                    LOG.warn("Two outcomes for one continuation, this one is swallowed: {}", response);
                } else {
                    LOG.warn("Two outcomes for one continuation, this one is swallowed:", failure);
                }
                return false;
            }
            FilterRunner.this.response = response;
            FilterRunner.this.failure = failure;
            responseNeedsProcessing = true;
            workResponse();
            return true;
        }
    }

    /**
     * {@link FilterContinuationImpl} that is adapted for legacy filters: Implements
     * {@link FilterChain}, and also implements the {@link Subscriber} that will subscribe to the
     * {@link HttpFilter#doFilter} return value.
     */
    private class FilterChainImpl extends FilterContinuationImpl<Publisher<MutableHttpResponse<?>>> implements ClientFilterChain, ServerFilterChain, Subscriber<HttpResponse<?>> {
        FilterChainImpl(int filterIndex, @Nullable SuspensionPoint<HttpResponse<?>> next) {
            super(filterIndex, next);
        }

        @Override
        public Publisher<? extends HttpResponse<?>> proceed(MutableHttpRequest<?> request) {
            return proceed((HttpRequest<?>) request);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        protected Publisher<MutableHttpResponse<?>> adapt() {
            return (Publisher) Mono.fromFuture(this);
        }

        @Override
        public void onSubscribe(Subscription s) {
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
            if (!forwardedResponse.get()) {
                forwardResponse(response, new IllegalStateException("Publisher did not return response"));
            }
        }
    }

    private class ReactiveContinuationImpl<R> extends FilterContinuationImpl<R> {
        private final Class<R> reactiveType;

        ReactiveContinuationImpl(int filterIndex, @Nullable SuspensionPoint<HttpResponse<?>> next, Class<R> reactiveType) {
            super(filterIndex, next);
            this.reactiveType = reactiveType;
        }

        @Override
        protected R adapt() {
            return Publishers.convertPublisher(Mono.fromFuture(this), reactiveType);
        }
    }

    private class BlockingContinuationImpl extends FilterContinuationImpl<HttpResponse<?>> {
        BlockingContinuationImpl(int filterIndex, @Nullable SuspensionPoint<HttpResponse<?>> next) {
            super(filterIndex, next);
        }

        @Override
        protected HttpResponse<?> adapt() {
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
