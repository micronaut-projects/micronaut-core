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
package io.micronaut.http.client.limit;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.client.exceptions.ConcurrencyLimitExceededException;
import io.micronaut.http.limit.LimiterState;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Caps the exchanges in flight of a service client, whatever the connections and HTTP/2 streams
 * they use. An exchange over the cap waits in a bounded queue, first come first served, for at
 * most the queue timeout; a full queue or a timeout fails the exchange with a
 * {@link ConcurrencyLimitExceededException} before it is sent.
 *
 * <p>A {@link Permit} is given back when run; only its first run has an effect. The caller
 * {@link Permit#take() takes} a permit it received: a waiting exchange that is cancelled leaves
 * the queue, and a permit granted to it but not taken yet is given back. A cancellation after the
 * permit was taken, e.g. of a {@code Mono} that cancels its source once it emitted, leaves the
 * permit with the caller.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class ClientConcurrencyLimiter implements LimiterState {

    private final String name;
    private final int maxConcurrent;
    private final int maxQueued;
    private final Duration queueTimeout;
    private final ArrayDeque<Waiter> queue = new ArrayDeque<>();
    private final LongAdder acquired = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private int inFlight;

    /**
     * @param name          The name, usually the service id
     * @param maxConcurrent The maximum number of exchanges in flight, at least 1
     * @param maxQueued     The maximum number of waiting exchanges, at least 0
     * @param queueTimeout  The maximum wait in the queue; zero or negative waits forever
     */
    public ClientConcurrencyLimiter(String name, int maxConcurrent, int maxQueued, Duration queueTimeout) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("max-concurrent-requests must be at least 1: " + maxConcurrent);
        }
        if (maxQueued < 0) {
            throw new IllegalArgumentException("max-queued-requests must not be negative: " + maxQueued);
        }
        this.name = Objects.requireNonNull(name, "name");
        this.maxConcurrent = maxConcurrent;
        this.maxQueued = maxQueued;
        this.queueTimeout = Objects.requireNonNull(queueTimeout, "queueTimeout");
    }

    /**
     * Acquire a permit, waiting in the queue if needed.
     *
     * @param scheduler The scheduler of the queue timeout
     * @return The flow of the permit, which the caller takes, see {@link Permit#take()}
     */
    public ExecutionFlow<Permit> acquire(ScheduledExecutorService scheduler) {
        Waiter waiter;
        synchronized (this) {
            if (inFlight < maxConcurrent) {
                inFlight++;
                acquired.increment();
                return ExecutionFlow.just(newPermit());
            }
            if (queue.size() >= maxQueued) {
                rejected.increment();
                return ExecutionFlow.error(new ConcurrencyLimitExceededException(name,
                    "The limit of " + maxConcurrent + " concurrent requests of [" + name + "] was reached, and " + maxQueued + " requests are waiting already", false));
            }
            waiter = new Waiter();
            queue.add(waiter);
        }
        if (queueTimeout.isPositive()) {
            waiter.timeout = scheduler.schedule(waiter::timeOut, queueTimeout.toNanos(), TimeUnit.NANOSECONDS);
        }
        waiter.flow.onCancel(waiter::cancel);
        return waiter.flow;
    }

    private Permit newPermit() {
        return new Permit();
    }

    private void release() {
        while (true) {
            Waiter next;
            synchronized (this) {
                next = queue.poll();
                if (next == null) {
                    inFlight--;
                    return;
                }
            }
            // the permit goes on to the next waiter, unless it gave up meanwhile
            if (next.grant()) {
                return;
            }
        }
    }

    private synchronized void remove(Waiter waiter) {
        queue.remove(waiter);
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * @return The maximum number of exchanges in flight
     */
    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    @Override
    public synchronized int getInFlight() {
        return inFlight;
    }

    @Override
    public synchronized long getAvailablePermits() {
        return Math.max(0, maxConcurrent - inFlight);
    }

    @Override
    public synchronized int getQueued() {
        return queue.size();
    }

    @Override
    public long getAcquiredCount() {
        return acquired.sum();
    }

    @Override
    public long getRejectedCount() {
        return rejected.sum();
    }

    /**
     * A permit of the limiter, given back when run.
     */
    public final class Permit extends AtomicInteger implements Runnable {
        private static final int GRANTED = 0;
        private static final int TAKEN = 1;
        private static final int RELEASED = 2;

        private Permit() {
        }

        /**
         * Take the permit over from the limiter: from here on, only the caller gives it back.
         *
         * @return {@code false} if the permit was already given back because the acquisition was
         * cancelled, then the caller must not proceed
         */
        public boolean take() {
            return compareAndSet(GRANTED, TAKEN);
        }

        /**
         * Give back a permit that the caller did not take.
         */
        void abandon() {
            if (compareAndSet(GRANTED, RELEASED)) {
                release();
            }
        }

        /**
         * Give the permit back. Only the first call has an effect.
         */
        @Override
        public void run() {
            if (getAndSet(RELEASED) != RELEASED) {
                release();
            }
        }
    }

    /**
     * An exchange waiting for a permit.
     */
    private final class Waiter extends AtomicInteger {
        private static final int WAITING = 0;
        private static final int GRANTED = 1;
        private static final int GAVE_UP = 2;

        final DelayedExecutionFlow<Permit> flow = DelayedExecutionFlow.create();
        volatile @Nullable ScheduledFuture<?> timeout;
        volatile @Nullable Permit permit;

        boolean grant() {
            if (!compareAndSet(WAITING, GRANTED)) {
                return false;
            }
            cancelTimeout();
            Permit granted = newPermit();
            permit = granted;
            acquired.increment();
            flow.complete(granted);
            if (flow.isCancelled()) {
                // cancelled while the permit was granted: the cancel hook may have missed it
                granted.abandon();
            }
            return true;
        }

        void timeOut() {
            if (compareAndSet(WAITING, GAVE_UP)) {
                remove(this);
                rejected.increment();
                flow.completeExceptionally(new ConcurrencyLimitExceededException(name,
                    "Timed out after " + queueTimeout + " waiting for one of the " + maxConcurrent + " concurrent requests of [" + name + "]", true));
            }
        }

        void cancel() {
            if (compareAndSet(WAITING, GAVE_UP)) {
                cancelTimeout();
                remove(this);
            } else {
                Permit granted = permit;
                if (granted != null) {
                    granted.abandon();
                }
            }
        }

        private void cancelTimeout() {
            ScheduledFuture<?> future = timeout;
            if (future != null) {
                future.cancel(false);
            }
        }
    }
}
