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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.RequestTimeoutException;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The deadline of a request whose route has a request timeout, see
 * {@link io.micronaut.http.annotation.RequestTimeout}. The handler of the route finds it with
 * {@link #find(HttpRequest)}, and may {@link #extend(Duration) extend} or {@link #clear() clear}
 * it, e.g. before it starts a long computation it knows is legitimate. The deadline ends when the
 * route produced its response: changing it afterwards has no effect.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class RequestDeadline {

    private static final String ATTRIBUTE = "micronaut.http.server.request-deadline";

    /**
     * The timer of the deadlines: it only claims the expiry and hands the response to the
     * executor of the deadline, so one daemon thread serves all servers.
     */
    private static final ScheduledThreadPoolExecutor TIMER;

    static {
        TIMER = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "micronaut-request-timeout");
            thread.setDaemon(true);
            return thread;
        });
        TIMER.setRemoveOnCancelPolicy(true);
    }

    private enum State { ACTIVE, CLEARED, FINISHED, EXPIRED }

    private final Executor executor;
    private final Runnable onExpiry;
    private final DelayedExecutionFlow<HttpResponse<?>> result = DelayedExecutionFlow.create();
    private State state = State.ACTIVE;
    private Duration timeout;
    private long deadlineNanos;
    private @Nullable ScheduledFuture<?> task;
    private @Nullable ExecutionFlow<HttpResponse<?>> upstream;

    private RequestDeadline(Duration timeout, Executor executor, Runnable onExpiry) {
        this.timeout = timeout;
        this.executor = executor;
        this.onExpiry = onExpiry;
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
     * @param request  The request
     * @param timeout  The timeout
     * @param executor Completes the response on expiry, off the timer thread
     * @param onExpiry Run on expiry, before the response fails, e.g. to release the request body
     * @return The started deadline
     */
    static RequestDeadline start(HttpRequest<?> request, Duration timeout, Executor executor, Runnable onExpiry) {
        RequestDeadline deadline = new RequestDeadline(timeout, executor, onExpiry);
        request.setAttribute(ATTRIBUTE, deadline);
        synchronized (deadline) {
            deadline.deadlineNanos = System.nanoTime() + timeout.toNanos();
            deadline.schedule();
        }
        return deadline;
    }

    /**
     * @return The current timeout, the extensions included
     */
    public synchronized Duration getTimeout() {
        return timeout;
    }

    /**
     * @return The time left until the deadline, empty once it was cleared or ended
     */
    public synchronized Optional<Duration> remaining() {
        if (state != State.ACTIVE) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofNanos(Math.max(0, deadlineNanos - System.nanoTime())));
    }

    /**
     * Move the deadline later.
     *
     * @param extension The time to add, not negative
     * @return Whether the deadline was extended: {@code false} once it was cleared or ended
     */
    public synchronized boolean extend(Duration extension) {
        Objects.requireNonNull(extension, "extension");
        if (extension.isNegative()) {
            throw new IllegalArgumentException("Negative extension: " + extension);
        }
        if (state != State.ACTIVE) {
            return false;
        }
        timeout = timeout.plus(extension);
        deadlineNanos += extension.toNanos();
        // the scheduled task finds the deadline moved and schedules itself again
        return true;
    }

    /**
     * Remove the deadline: the route has no timeout any more.
     *
     * @return Whether the deadline was cleared: {@code false} once it ended
     */
    public synchronized boolean clear() {
        if (state == State.ACTIVE) {
            state = State.CLEARED;
            cancelTask();
            return true;
        }
        return state == State.CLEARED;
    }

    /**
     * Bind the response of the route to this deadline.
     *
     * @param flow    The response of the route
     * @param discard Releases a response that arrives after the expiry
     * @return The response, or the failure of the expiry
     */
    ExecutionFlow<HttpResponse<?>> bind(ExecutionFlow<HttpResponse<?>> flow, Consumer<HttpResponse<?>> discard) {
        if (flow.tryComplete() != null) {
            if (finish()) {
                return flow;
            }
            flow.onComplete((response, error) -> {
                if (response != null) {
                    discard.accept(response);
                }
            });
            return result;
        }
        boolean expired;
        synchronized (this) {
            upstream = flow;
            expired = state == State.EXPIRED;
        }
        if (expired) {
            flow.cancel();
        }
        flow.onComplete((response, error) -> {
            if (finish()) {
                if (error != null) {
                    result.completeExceptionally(error);
                } else {
                    result.complete(response);
                }
            } else if (response != null) {
                discard.accept(response);
            }
        });
        result.onCancel(flow::cancel);
        return result;
    }

    /**
     * End the deadline: the route produced its response.
     *
     * @return Whether the route won against the expiry
     */
    private synchronized boolean finish() {
        if (state == State.EXPIRED) {
            return false;
        }
        state = State.FINISHED;
        cancelTask();
        upstream = null;
        return true;
    }

    private void schedule() {
        long delay = deadlineNanos - System.nanoTime();
        task = TIMER.schedule(this::fire, Math.max(0, delay), TimeUnit.NANOSECONDS);
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
        synchronized (this) {
            if (state != State.ACTIVE) {
                return;
            }
            if (deadlineNanos - System.nanoTime() > 0) {
                // extended meanwhile
                schedule();
                return;
            }
            state = State.EXPIRED;
            task = null;
            toCancel = upstream;
            upstream = null;
            expired = timeout;
        }
        executor.execute(() -> {
            if (toCancel != null) {
                toCancel.cancel();
            }
            try {
                onExpiry.run();
            } finally {
                result.completeExceptionally(new RequestTimeoutException(expired));
            }
        });
    }
}
