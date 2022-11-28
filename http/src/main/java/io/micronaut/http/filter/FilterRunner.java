package io.micronaut.http.filter;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.execution.CompletableFutureExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.execution.ImperativeExecutionFlow;
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

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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

    private final List<InternalFilter> filters;

    private HttpRequest<?> request;
    private HttpResponse<?> response;
    private Throwable failure;
    private SuspensionPoint<HttpResponse<?>> responseSuspensionPoint = new SuspensionPoint<>(-1, null);
    private int index;
    private boolean responseNeedsProcessing = false;

    private static final Comparator<InternalFilter> SORT = Comparator.comparingInt(f -> {
        if (f instanceof InternalFilter.Before<?> before) {
            return before.order().getOrder(before.bean());
        } else if (f instanceof InternalFilter.After<?> after) {
            return after.order().getOrder(after.bean());
        } else if (f instanceof InternalFilter.AroundLegacy around) {
            return around.order().getOrder(around.bean());
        } else {
            // terminal ops shouldn't appear when sort is called
            throw new IllegalStateException("Filter cannot be ordered: " + f);
        }
    });
    private static final Comparator<InternalFilter> REVERSE_SORT = SORT.reversed();

    public FilterRunner(List<InternalFilter> filters) {
        this.filters = filters;
    }

    public static void sort(List<InternalFilter> filters) {
        filters.sort(SORT);
    }

    public static void sortReverse(List<InternalFilter> filters) {
        filters.sort(REVERSE_SORT);
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

    public final ExecutionFlow<? extends HttpResponse<?>> run(HttpRequest<?> request) {
        if (this.request != null) {
            throw new IllegalStateException("Can only process one request");
        }
        this.request = request;

        ExecutionFlow<HttpResponse<?>> resultFlow = CompletableFutureExecutionFlow.just(responseSuspensionPoint);
        workRequest();
        return resultFlow;
    }

    @SuppressWarnings("StatementWithEmptyBody")
    private void workRequest() {
        while (true) {
            InternalFilter filter = filters.get(index++);
            if (filter instanceof InternalFilter.Before<?> before) {
                if (!invokeBefore(before)) {
                    // suspend
                    return;
                }
                // continue with next filter
            } else if (filter instanceof InternalFilter.After<?>) {
                // skip filter, only used for response
            } else if (filter instanceof InternalFilter.AroundLegacy around) {
                FilterChainImpl chainSuspensionPoint = new FilterChainImpl(index - 1, responseSuspensionPoint);
                responseSuspensionPoint = chainSuspensionPoint;
                try {
                    Publisher<? extends HttpResponse<?>> result = around.bean().doFilter(request, chainSuspensionPoint);
                    result.subscribe(new LegacyFilterSubscriber());
                } catch (Throwable e) {
                    if (chainSuspensionPoint.decidedOnBranch.compareAndSet(false, true)) {
                        // proceed wasn't called, continue directly to response processing
                        // cancel the suspension point, since we don't do more request processing
                        responseSuspensionPoint = chainSuspensionPoint.next;
                        failure = e;
                        responseNeedsProcessing = true;
                        workResponse();
                    } else {
                        // proceed was called, need to wait for completion to avoid concurrency issues
                        // filters should really not do this: Either throw the exception before the proceed call, or emit it from the returned publisher
                        chainSuspensionPoint.whenComplete((resp, err) -> {
                            if (err == null) {
                                LOG.warn("Filter method threw exception after chain.proceed() had already been called. This can lead to memory leaks, please fix your filter!", e);
                                failure = e;
                            } else {
                                LOG.warn("Filter method threw exception after chain.proceed() had already been called. Downstream handlers also threw an exception, that one is being forwarded.", e);
                                failure = err;
                            }
                            responseNeedsProcessing = true;
                            workResponse();
                        });
                    }
                }
                // suspend
                return;
            } else if (filter instanceof InternalFilter.TerminalReactive || filter instanceof InternalFilter.Terminal) {
                ExecutionFlow<? extends HttpResponse<?>> terminalFlow;
                if (filter instanceof InternalFilter.Terminal t) {
                    try {
                        terminalFlow = t.responseFlow().apply(request);
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
                return;
            } else {
                throw new IllegalStateException("Unknown filter type");
            }
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
            }
        }
    }

    private <T> boolean invokeBefore(InternalFilter.Before<T> before) {
        // todo: handle ExecuteOn
        try {
            Object returnValue = before.method().invoke(before.bean(), satisfy(before.method().getArguments(), false));
            return handleFilterReturn(returnValue, true);
        } catch (Throwable e) {
            failure = e;
            responseNeedsProcessing = true;
            workResponse();
            return false;
        }
    }

    private <T> boolean invokeAfter(InternalFilter.After<T> after) {
        // todo: handle ExecuteOn
        try {
            Object returnValue = after.method().invoke(after.bean(), satisfy(after.method().getArguments(), true));
            return handleFilterReturn(returnValue, true);
        } catch (Throwable e) {
            failure = e;
            responseNeedsProcessing = true;
        }
        return true;
    }

    /**
     * @param requestFilter Whether this is a request filter
     * @return {@code true} if we can continue with the next filter, {@code false} if we should
     * suspend
     */
    private boolean handleFilterReturn(Object returnValue, boolean requestFilter) throws Throwable {
        if (returnValue instanceof Optional<?> opt) {
            returnValue = opt.orElse(null);
        }
        if (returnValue == null) {
            return true;
        }
        if (requestFilter) {
            if (returnValue instanceof HttpRequest<?> req) {
                this.request = req;
                return true;
            } else if (returnValue instanceof HttpResponse<?> resp) {
                // cancel request pipeline, move immediately to response handling
                this.response = resp;
                this.responseNeedsProcessing = true;
                workResponse();
                return false;
            }
        } else {
            if (returnValue instanceof HttpResponse<?> resp) {
                // cancel request pipeline, move immediately to response handling
                this.response = resp;
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
            return handleFilterReturn(doneFlow.getValue(), requestFilter);
        } else {
            // suspend until flow completes
            delayedFlow.onComplete((v, e) -> {
                if (e == null) {
                    try {
                        if (handleFilterReturn(v, requestFilter)) {
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
                failure = e;
                responseNeedsProcessing = true;
                workResponse();
            });
            return false;
        }
    }

    private Object[] satisfy(
        Argument<?>[] arguments,
        boolean hasResponse
    ) throws Exception {
        Object[] fulfilled = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            Argument<?> argument = arguments[i];
            if (argument.getType().isAssignableFrom(MutableHttpRequest.class)) {
                fulfilled[i] = request;
            } else if (argument.getType().isAssignableFrom(MutableHttpResponse.class)) {
                if (!hasResponse) {
                    throw new IllegalStateException("Filter is called before the response is known, can't have a response argument");
                }
                fulfilled[i] = response;
            } else {
                throw new IllegalStateException("Unsupported filter argument type: " + argument);
            }
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

    private class FilterChainImpl extends SuspensionPoint<HttpResponse<?>> implements ClientFilterChain, ServerFilterChain {
        private final AtomicBoolean decidedOnBranch = new AtomicBoolean(false);

        FilterChainImpl(int filterIndex, @Nullable SuspensionPoint<HttpResponse<?>> next) {
            super(filterIndex, next);
        }

        @Override
        public Publisher<? extends HttpResponse<?>> proceed(MutableHttpRequest<?> request) {
            return proceed((HttpRequest<?>) request);
        }

        @SuppressWarnings({"RedundantCast", "unchecked", "rawtypes"})
        @Override
        public Publisher<MutableHttpResponse<?>> proceed(HttpRequest<?> request) {
            FilterRunner.this.request = request;
            return ReactiveExecutionFlow.toPublisher(() -> {
                if (decidedOnBranch.compareAndSet(false, true)) {
                    workRequest();
                    return (ExecutionFlow) CompletableFutureExecutionFlow.just(this);
                } else {
                    throw new IllegalStateException("Already subscribed to proceed() publisher, or filter method threw an exception and was cancelled");
                }
            });
        }
    }

    private class LegacyFilterSubscriber implements Subscriber<HttpResponse<?>> {
        boolean hasResponse = false;

        @Override
        public void onSubscribe(Subscription s) {
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(HttpResponse<?> response) {
            if (!hasResponse) {
                FilterRunner.this.response = response;
                responseNeedsProcessing = true;
                hasResponse = true;
                workResponse();
            }
        }

        @Override
        public void onError(Throwable t) {
            if (!hasResponse) {
                FilterRunner.this.failure = t;
                responseNeedsProcessing = true;
                hasResponse = true;
                workResponse();
            } else {
                LOG.warn("Swallowing filter error", t);
            }
        }

        @Override
        public void onComplete() {
            if (!hasResponse) {
                FilterRunner.this.failure = new IllegalStateException("Publisher did not return response");
                hasResponse = true;
                workResponse();
            }
        }
    }
}
