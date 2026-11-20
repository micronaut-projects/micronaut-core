/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.server;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.http.server.exceptions.RequestTimeoutException;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The deadline of a request whose route has a request timeout, see
 * {@link io.micronaut.http.annotation.RequestTimeout}: a read-only view for the handler of the
 * route, which finds it with {@link #find(HttpRequest)}. Work that may outlive the response,
 * e.g. a blocking loop the server cannot interrupt, can check {@link #isExpired()} or
 * {@link #remaining()} and stop once the request was answered.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class RequestDeadline {

    private static final String ATTRIBUTE = "micronaut.http.server.request-deadline";

    /**
     * The timer of the deadlines: it only claims the expiry and hands the response to
     * {@link #DISPATCH}, so one daemon thread serves all servers.
     */
    private static final ScheduledThreadPoolExecutor TIMER;

    /**
     * Answers the requests whose deadline expired. It is separate from the executors of the
     * routes, e.g. a fixed blocking pool, whose threads may all be held by the routes that time
     * out: the response of the expiry must not queue behind the work it times out.
     */
    private static final ThreadPoolExecutor DISPATCH;

    static {
        TIMER = new ScheduledThreadPoolExecutor(1, daemon("micronaut-request-timeout"));
        TIMER.setRemoveOnCancelPolicy(true);
        DISPATCH = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS, new SynchronousQueue<>(),
            daemon("micronaut-request-timeout-dispatch"));
    }

    private enum State { ACTIVE, FINISHED, EXPIRED, ABANDONED }

    private final PropagatedContext propagatedContext;
    private final Runnable onExpiry;
    /**
     * Fails with the {@link RequestTimeoutException} when the deadline expires.
     */
    private final CompletableFuture<HttpResponse<?>> expiry = new CompletableFuture<>();
    private State state = State.ACTIVE;
    private final Duration timeout;
    private long deadlineNanos;
    private @Nullable ScheduledFuture<?> task;
    /**
     * The response of a route that is not reactive, cancelled on expiry. It is published only
     * after the deadline listens to its completion: a delayed flow accepts no listener after it
     * was cancelled.
     */
    private @Nullable ExecutionFlow<HttpResponse<?>> upstream;
    /**
     * Run once when the deadline ends, however it ends, e.g. removes the abandonment hook.
     */
    private @Nullable Runnable onEnd;

    private RequestDeadline(Duration timeout, PropagatedContext propagatedContext, Runnable onExpiry) {
        this.timeout = timeout;
        this.propagatedContext = propagatedContext;
        this.onExpiry = onExpiry;
    }

    private static ThreadFactory daemon(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * The deadline of the request, if its route has a request timeout and is executing.
     *
     * @param request The request
     * @return The deadline
     */
    public static Optional<RequestDeadline> find(HttpRequest<?> request) {
        return request.getAttribute(ATTRIBUTE, RequestDeadline.class);
    }

    /**
     * Start the deadline of a request.
     *
     * @param request           The request
     * @param timeout           The timeout
     * @param propagatedContext The context the expiry is answered in
     * @param onExpiry          Run on expiry, before the response fails, e.g. to release the request body
     * @return The started deadline
     */
    static RequestDeadline start(HttpRequest<?> request, Duration timeout, PropagatedContext propagatedContext, Runnable onExpiry) {
        RequestDeadline deadline = new RequestDeadline(timeout, propagatedContext, onExpiry);
        request.setAttribute(ATTRIBUTE, deadline);
        deadline.schedule();
        return deadline;
    }

    private synchronized void schedule() {
        deadlineNanos = System.nanoTime() + timeout.toNanos();
        task = TIMER.schedule(this::fire, timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    /**
     * @return The timeout of the route
     */
    public synchronized Duration getTimeout() {
        return timeout;
    }

    /**
     * @return The time left until the deadline, empty once the deadline ended: the route produced
     * its response, the deadline expired, or the client abandoned the request
     */
    public synchronized Optional<Duration> remaining() {
        if (state != State.ACTIVE) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofNanos(Math.max(0, deadlineNanos - System.nanoTime())));
    }

    /**
     * Whether the deadline expired: the request was answered with the timeout, and the result
     * of the route is discarded.
     *
     * @return Whether the deadline expired
     */
    public synchronized boolean isExpired() {
        return state == State.EXPIRED;
    }

    /**
     * Run an action once when the deadline ends: the route produced its response, the deadline
     * expired, or the client abandoned the request.
     *
     * @param action The action
     */
    void onEnd(Runnable action) {
        boolean ended;
        synchronized (this) {
            ended = state != State.ACTIVE;
            if (!ended) {
                onEnd = action;
            }
        }
        if (ended) {
            runQuietly(action);
        }
    }

    /**
     * The client abandoned the request, e.g. closed the connection: the deadline stops, without
     * answering the request, and the route is cancelled.
     */
    void abandon() {
        ExecutionFlow<HttpResponse<?>> toCancel;
        Runnable end;
        synchronized (this) {
            if (state != State.ACTIVE) {
                return;
            }
            state = State.ABANDONED;
            cancelTask();
            toCancel = upstream;
            upstream = null;
            end = takeOnEnd();
        }
        if (toCancel != null) {
            toCancel.cancel();
        }
        runQuietly(end);
    }

    /**
     * Bind the response of the route to this deadline.
     *
     * @param flow    The response of the route
     * @param discard Releases a response that arrives after the expiry
     * @return The response, or the failure of the expiry
     */
    @SuppressWarnings("unchecked")
    ExecutionFlow<HttpResponse<?>> bind(ExecutionFlow<HttpResponse<?>> flow, Consumer<HttpResponse<?>> discard) {
        if (flow instanceof ReactiveExecutionFlow<?> reactive) {
            return bindReactive((ReactiveExecutionFlow<HttpResponse<?>>) reactive, discard);
        }
        if (flow.tryComplete() != null) {
            if (finish()) {
                return flow;
            }
            flow.onComplete((response, error) -> {
                // a failed or empty route has no response to release
                if (error == null) {
                    discard.accept(response);
                }
            });
            return expiryFlow();
        }
        DelayedExecutionFlow<HttpResponse<?>> result = DelayedExecutionFlow.create();
        // listen first: the flow may only be cancelled once its listener is in place
        flow.onComplete((response, error) -> {
            if (finish()) {
                if (error != null) {
                    result.tryCompleteExceptionally(error);
                } else {
                    result.tryComplete(response);
                }
            } else if (error == null) {
                discard.accept(response);
            }
        });
        boolean cancel;
        synchronized (this) {
            cancel = state == State.EXPIRED || state == State.ABANDONED;
            if (!cancel && state != State.FINISHED) {
                upstream = flow;
            }
        }
        if (cancel) {
            flow.cancel();
        }
        expiry.whenComplete((ignored, error) -> {
            if (error != null) {
                result.tryCompleteExceptionally(error);
            }
        });
        result.onCancel(flow::cancel);
        return result;
    }

    /**
     * Bind a reactive response: the route is subscribed when the response is, in the Reactor
     * context of the subscriber, e.g. the one a reactive filter writes, and the expiry cancels
     * its subscription.
     */
    private ExecutionFlow<HttpResponse<?>> bindReactive(ReactiveExecutionFlow<HttpResponse<?>> flow, Consumer<HttpResponse<?>> discard) {
        Mono<HttpResponse<?>> route = Mono.from(flow.toPublisher())
            .<HttpResponse<?>>flatMap(response -> {
                if (finish()) {
                    return Mono.just(response);
                }
                discard.accept(response);
                return Mono.never();
            })
            .switchIfEmpty(Mono.defer(() -> finish() ? Mono.empty() : Mono.never()))
            .onErrorResume(error -> finish() ? Mono.error(error) : Mono.never());
        Mono<HttpResponse<?>> expired = Mono.fromFuture(expiry, true);
        return ReactiveExecutionFlow.fromPublisher(Mono.firstWithSignal(route, expired));
    }

    private ExecutionFlow<HttpResponse<?>> expiryFlow() {
        DelayedExecutionFlow<HttpResponse<?>> result = DelayedExecutionFlow.create();
        expiry.whenComplete((ignored, error) -> result.tryCompleteExceptionally(
            error == null ? new IllegalStateException("The deadline ended without expiring") : error));
        return result;
    }

    /**
     * End the deadline: the route produced its response.
     *
     * @return Whether the route won against the expiry
     */
    private boolean finish() {
        Runnable end;
        synchronized (this) {
            if (state == State.EXPIRED || state == State.ABANDONED) {
                return false;
            }
            if (state == State.FINISHED) {
                return true;
            }
            state = State.FINISHED;
            cancelTask();
            upstream = null;
            end = takeOnEnd();
        }
        runQuietly(end);
        return true;
    }

    private @Nullable Runnable takeOnEnd() {
        Runnable end = onEnd;
        onEnd = null;
        return end;
    }

    private static void runQuietly(@Nullable Runnable action) {
        if (action != null) {
            try {
                action.run();
            } catch (RuntimeException e) {
                // the hooks only release: a failure must not change the response
            }
        }
    }

    private void cancelTask() {
        ScheduledFuture<?> t = task;
        if (t != null) {
            t.cancel(false);
            task = null;
        }
    }

    private void fire() {
        ExecutionFlow<HttpResponse<?>> toCancel;
        Duration expired;
        Runnable end;
        synchronized (this) {
            if (state != State.ACTIVE) {
                return;
            }
            state = State.EXPIRED;
            task = null;
            toCancel = upstream;
            upstream = null;
            expired = timeout;
            end = takeOnEnd();
        }
        Runnable answer = propagatedContext.wrap(() -> {
            if (toCancel != null) {
                toCancel.cancel();
            }
            try {
                onExpiry.run();
            } finally {
                runQuietly(end);
                expiry.completeExceptionally(new RequestTimeoutException(expired));
            }
        });
        try {
            DISPATCH.execute(answer);
        } catch (RejectedExecutionException e) {
            // never leave the response pending
            answer.run();
        }
    }
}
