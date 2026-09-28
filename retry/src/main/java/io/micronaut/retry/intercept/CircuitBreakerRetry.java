/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.retry.intercept;

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.retry.CircuitBreakerSnapshot;
import io.micronaut.retry.CircuitBreakerWindow;
import io.micronaut.retry.CircuitState;
import io.micronaut.retry.RetryState;
import io.micronaut.retry.RetryStateBuilder;
import io.micronaut.retry.annotation.RetryPredicate;
import io.micronaut.retry.event.CircuitClosedEvent;
import io.micronaut.retry.event.CircuitOpenEvent;
import io.micronaut.retry.exception.CircuitOpenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A context object for storing the state of the Circuit.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class CircuitBreakerRetry implements MutableRetryState {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultRetryInterceptor.class);

    private final RetryStateBuilder retryStateBuilder;
    private final Circuit circuit;
    private final long openTimeout;
    private final ExecutableMethod<?, ?> method;
    @Nullable
    private final ApplicationEventPublisher eventPublisher;
    private final boolean throwWrappedException;
    private final AtomicReference<CircuitState> state;
    private volatile MutableRetryState childState;

    /**
     * Creates a circuit breaker retry state.
     *
     * @param openTimeout The circuit open timeout in millis
     * @param childStateBuilder The retry state builder
     * @param method A compile time produced invocation of a method call
     * @param eventPublisher To publish circuit events
     * @param throwWrappedException If {@code true}, the original exception will be wrapped in {@link CircuitOpenException}
     */
    public CircuitBreakerRetry(long openTimeout,
                               RetryStateBuilder childStateBuilder,
                               ExecutableMethod<?, ?> method,
                               @Nullable ApplicationEventPublisher eventPublisher,
                               boolean throwWrappedException) {
        this(new Circuit(openTimeout), childStateBuilder, method, eventPublisher, throwWrappedException);
    }

    /**
     * Creates a circuit breaker retry state over a circuit that other retry states can share,
     * e.g. the circuit of a named circuit breaker: they open, half-open and close it together,
     * and each has its own retries.
     *
     * @param circuit The circuit
     * @param childStateBuilder The retry state builder
     * @param method A compile time produced invocation of a method call
     * @param eventPublisher To publish circuit events
     * @param throwWrappedException If {@code true}, the original exception will be wrapped in {@link CircuitOpenException}
     * @since 5.3.0
     */
    public CircuitBreakerRetry(Circuit circuit,
                               RetryStateBuilder childStateBuilder,
                               ExecutableMethod<?, ?> method,
                               @Nullable ApplicationEventPublisher eventPublisher,
                               boolean throwWrappedException) {
        this.circuit = circuit;
        this.state = circuit.state;
        this.retryStateBuilder = childStateBuilder;
        this.openTimeout = circuit.openTimeout;
        this.childState = (MutableRetryState) childStateBuilder.build();
        this.eventPublisher = eventPublisher;
        this.method = method;
        this.throwWrappedException = throwWrappedException;
    }

    /**
     * The retry state of one call. Without a rolling window it is this state, shared by every
     * call, as the circuit breaker of Micronaut always did. With one, it is a new state that
     * holds the permit of the call and its own retries, so that the outcome of a call that
     * started in an older state of the circuit counts for nothing.
     *
     * @return The retry state of a call
     * @since 5.3.0
     */
    public MutableRetryState newInvocation() {
        CircuitBreakerWindow window = circuit.window;
        return window == null ? this : new WindowedInvocation(this, window);
    }

    @Override
    public void close(@Nullable Throwable exception) {
        requireNoWindow();
        if (exception == null && currentState() == CircuitState.HALF_OPEN) {
            closeCircuit();
        } else if (currentState() != CircuitState.OPEN) {
            if (exception != null && getRetryPredicate().test(exception)) {
                openCircuit(exception);
            } else {
                // reset state for successful operation
                circuit.time = System.currentTimeMillis();
                circuit.lastError = null;
                this.childState = (MutableRetryState) retryStateBuilder.build();
            }
        }
    }

    @Override
    public void open() {
        requireNoWindow();
        // the state first: an open circuit whose timeout elapsed half-opens, and forgets its error
        boolean open = currentState() == CircuitState.OPEN;
        Throwable lastError = circuit.lastError;
        if (open && lastError != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Rethrowing existing exception for Open Circuit [{}]: {}", method, lastError.getMessage());
            }
            if (lastError instanceof RuntimeException exception && !throwWrappedException) {
                throw exception;
            } else {
                throw new CircuitOpenException("Circuit Open: " + lastError.getMessage(), lastError);
            }
        }
    }

    @Override
    public long nextDelay() {
        return childState.nextDelay();
    }

    @Override
    public boolean canRetry(Throwable exception) {
        if (exception == null) {
            throw new IllegalArgumentException("Exception cause cannot be null");
        }
        requireNoWindow();
        try {
            return currentState() != CircuitState.OPEN && childState.canRetry(exception);
        } finally {
            if (currentState() == CircuitState.HALF_OPEN) {
                openCircuit(exception);
            }
        }
    }

    @Override
    public int getMaxAttempts() {
        return childState.getMaxAttempts();
    }

    @Override
    public int currentAttempt() {
        return childState.currentAttempt();
    }

    @Override
    public OptionalDouble getMultiplier() {
        return childState.getMultiplier();
    }

    @Override
    public Duration getDelay() {
        return childState.getDelay();
    }

    @Override
    public Duration getOverallDelay() {
        return childState.getOverallDelay();
    }

    @Override
    public Optional<Duration> getMaxDelay() {
        return childState.getMaxDelay();
    }

    @Override
    public RetryPredicate getRetryPredicate() {
        return childState.getRetryPredicate();
    }

    @Override
    @Nullable
    public Class<? extends Throwable> getCapturedException() {
        return childState.getCapturedException();
    }

    @Override
    public OptionalDouble getJitter() {
        return childState.getJitter();
    }

    /**
     * Returns the current circuit state.
     *
     * @return The current state
     */
    @Nullable
    public CircuitState currentState() {
        if (circuit.window != null) {
            return circuit.windowState();
        }
        if (state.get() == CircuitState.OPEN) {
            long now = System.currentTimeMillis();
            long timeout = circuit.time + openTimeout;
            if (now > timeout) {
                return halfOpenCircuit();
            }
            return state.get();
        } else {
            return state.get();
        }
    }

    /**
     * @return The circuit of this state
     * @since 5.3.0
     */
    public final Circuit circuit() {
        return circuit;
    }

    /**
     * @return Whether the error of an open circuit is wrapped in a {@link CircuitOpenException}
     * @since 5.3.0
     */
    public final boolean isThrowWrappedException() {
        return throwWrappedException;
    }

    private void requireNoWindow() {
        if (circuit.window != null) {
            throw new IllegalStateException("A circuit with a rolling window is used through the retry state of each call, see newInvocation()");
        }
    }

    /**
     * Opens the circuit.
     *
     * @return The current state
     */
    private CircuitState openCircuit(Throwable cause) {
        if (cause == null) {
            throw new IllegalArgumentException("Exception cause cannot be null");
        }
        this.childState = (MutableRetryState) retryStateBuilder.build();
        circuit.lastError = cause;
        circuit.time = System.currentTimeMillis();
        circuit.changed(true);
        try {
            return state.getAndSet(CircuitState.OPEN);
        } finally {
            publishOpened(childState, cause);
        }
    }

    /**
     * Publish the event of a circuit that opened.
     *
     * @param retryState The retry state of the call that opened it
     * @param cause      The failure that opened it
     */
    final void publishOpened(RetryState retryState, Throwable cause) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Opening Circuit Breaker [{}] due to error: {}", method, cause.getMessage());
        }
        if (eventPublisher != null) {
            try {
                eventPublisher.publishEvent(new CircuitOpenEvent(method, retryState, cause));
            } catch (Exception e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error publishing CircuitOpen event: {}", e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Publish the event of a circuit that closed.
     */
    final void publishClosed() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Closing Circuit Breaker [{}]", method);
        }
        if (eventPublisher != null) {
            try {
                eventPublisher.publishEvent(new CircuitClosedEvent(method));
            } catch (Exception e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error publishing CircuitClosedEvent: {}", e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Resets the circuit state to {@link CircuitState#CLOSED}.
     *
     * @return The current state
     */
    private CircuitState closeCircuit() {
        circuit.time = System.currentTimeMillis();
        circuit.lastError = null;
        circuit.changed(false);
        this.childState = (MutableRetryState) retryStateBuilder.build();
        try {
            return state.getAndSet(CircuitState.CLOSED);
        } finally {
            publishClosed();
        }
    }

    /**
     * Resets the circuit state to {@link CircuitState#HALF_OPEN}.
     *
     * @return The current state
     */
    private CircuitState halfOpenCircuit() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Half Opening Circuit Breaker [{}]", method);
        }
        circuit.lastError = null;
        circuit.changed(false);
        this.childState = (MutableRetryState) retryStateBuilder.build();
        return state.getAndSet(CircuitState.HALF_OPEN);
    }

    /**
     * The retry state of one call of a circuit with a rolling window: it takes a permit of the
     * circuit when the call starts, see {@link #open()}, and reports the outcome of the call
     * once, with the generation of its permit, so that the outcome of a call that started in an
     * older state of the circuit counts for nothing.
     */
    private static final class WindowedInvocation implements MutableRetryState {

        private static final long NO_PERMIT = Long.MIN_VALUE;

        private final CircuitBreakerRetry owner;
        private final CircuitBreakerWindow window;
        private final MutableRetryState childState;
        private final AtomicBoolean ended = new AtomicBoolean();
        private volatile long permit = NO_PERMIT;

        WindowedInvocation(CircuitBreakerRetry owner, CircuitBreakerWindow window) {
            this.owner = owner;
            this.window = window;
            this.childState = (MutableRetryState) owner.retryStateBuilder.build();
        }

        @Override
        public void open() {
            permit = owner.circuit.acquire(owner.throwWrappedException);
        }

        @Override
        public boolean canRetry(Throwable exception) {
            if (exception == null) {
                throw new IllegalArgumentException("Exception cause cannot be null");
            }
            // the outcome of the call counts once it ends, see close
            return owner.circuit.windowState() != CircuitState.OPEN && childState.canRetry(exception);
        }

        @Override
        public void close(@Nullable Throwable exception) {
            // an exception the predicate of the retries rejects, e.g. one of the excludes, is a success
            boolean failure = exception != null && childState.getRetryPredicate().test(exception) && window.isFailure(exception);
            end(exception, failure);
        }

        @Override
        public void onUncaptured(Throwable exception) {
            end(exception, window.isFailure(exception));
        }

        @Override
        public void onCancel() {
            release();
        }

        @Override
        public void release() {
            if (ended.compareAndSet(false, true)) {
                owner.circuit.release(permit);
            }
        }

        private void end(@Nullable Throwable exception, boolean failure) {
            if (ended.compareAndSet(false, true)) {
                owner.circuit.record(permit, exception, failure, owner, childState);
            }
        }

        @Override
        public long nextDelay() {
            return childState.nextDelay();
        }

        @Override
        public int getMaxAttempts() {
            return childState.getMaxAttempts();
        }

        @Override
        public int currentAttempt() {
            return childState.currentAttempt();
        }

        @Override
        public OptionalDouble getMultiplier() {
            return childState.getMultiplier();
        }

        @Override
        public Duration getDelay() {
            return childState.getDelay();
        }

        @Override
        public Duration getOverallDelay() {
            return childState.getOverallDelay();
        }

        @Override
        public Optional<Duration> getMaxDelay() {
            return childState.getMaxDelay();
        }

        @Override
        public RetryPredicate getRetryPredicate() {
            return childState.getRetryPredicate();
        }

        @Override
        public @Nullable Class<? extends Throwable> getCapturedException() {
            return childState.getCapturedException();
        }

        @Override
        public OptionalDouble getJitter() {
            return childState.getJitter();
        }
    }

    /**
     * The state of a circuit: closed, open or half-open, with the error that opened it. Several
     * {@link CircuitBreakerRetry} states can share one, e.g. the users of a named circuit
     * breaker, see {@link io.micronaut.retry.CircuitBreakerRegistry}.
     *
     * <p>Without a {@link CircuitBreakerWindow window}, the circuit behaves as the circuit
     * breaker of Micronaut always did. With one, the outcomes of the last calls decide, see
     * {@link CircuitBreakerWindow}; every change of its state happens under the lock of the
     * circuit, and every change starts a new generation, so that the outcome of a call that took
     * a permit of an older state never counts. A half-open circuit whose trial permits have all
     * been taken for longer than the reset timeout, e.g. by calls that never report an outcome,
     * gives new trial permits.</p>
     *
     * @since 5.3.0
     */
    @Internal
    public static final class Circuit {

        private final long openTimeout;
        private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
        private final @Nullable CircuitBreakerWindow window;
        @Nullable
        private volatile Throwable lastError;
        private volatile long time = System.currentTimeMillis();
        private final boolean[] outcomes;
        private int next;
        private int calls;
        private int failures;
        private int trials;
        private int successes;
        @Nullable
        private Throwable lastFailure;
        private long generation;
        private final AtomicLong openedCount = new AtomicLong();
        private volatile long since = System.currentTimeMillis();

        /**
         * @param openTimeout The time the circuit stays open before it half-opens, in millis
         */
        public Circuit(long openTimeout) {
            this(openTimeout, null);
        }

        /**
         * @param openTimeout The time the circuit stays open before it half-opens, in millis
         * @param window      The rolling window, or {@code null} for the circuit breaker of Micronaut
         */
        public Circuit(long openTimeout, @Nullable CircuitBreakerWindow window) {
            this.openTimeout = openTimeout;
            this.window = window;
            this.outcomes = new boolean[window == null ? 0 : window.requestVolumeThreshold()];
        }

        /**
         * @return The time the circuit stays open before it half-opens, in millis
         */
        public long getOpenTimeout() {
            return openTimeout;
        }

        /**
         * @return The rolling window of the circuit, or {@code null}
         */
        public @Nullable CircuitBreakerWindow getWindow() {
            return window;
        }

        /**
         * @return The state of the circuit, as it was last changed: an open circuit whose timeout
         * elapsed half-opens on the next use
         */
        public CircuitState getState() {
            CircuitState current = state.get();
            return current == null ? CircuitState.CLOSED : current;
        }

        /**
         * @return Whether the circuit has been open for longer than its timeout
         */
        public boolean hasOpenTimeoutElapsed() {
            return System.currentTimeMillis() > time + openTimeout;
        }

        /**
         * @param name The name of the circuit
         * @return The state and the counters of the circuit: an open circuit whose timeout
         * elapsed is half-open
         */
        public synchronized CircuitBreakerSnapshot snapshot(String name) {
            CircuitState current = getState();
            if (current == CircuitState.OPEN && hasOpenTimeoutElapsed()) {
                current = CircuitState.HALF_OPEN;
            }
            return new CircuitBreakerSnapshot(name, current, window == null ? 0 : window.requestVolumeThreshold(),
                calls, failures, trials, successes, openedCount.get(), Instant.ofEpochMilli(since));
        }

        /**
         * A change of the state of a circuit without a window.
         *
         * @param opened Whether it opened
         */
        void changed(boolean opened) {
            if (opened) {
                openedCount.incrementAndGet();
            }
            since = System.currentTimeMillis();
        }

        /**
         * @return The state of a circuit with a window: half-open once the timeout of an open
         * circuit elapsed, and with new trial permits once the trials of a half-open circuit
         * took longer than the timeout
         */
        synchronized CircuitState windowState() {
            CircuitState current = getState();
            if (current == CircuitState.OPEN && hasOpenTimeoutElapsed()) {
                halfOpen();
            } else if (current == CircuitState.HALF_OPEN
                && trials >= Objects.requireNonNull(window).successThreshold()
                && hasOpenTimeoutElapsed()) {
                halfOpen();
            }
            return getState();
        }

        /**
         * Take a permit for a call of a circuit with a window: always in a closed circuit, one
         * of the trial permits in a half-open one. The call reports its outcome with
         * {@link #record}, or returns the permit with {@link #release(long)}.
         *
         * @param throwWrappedException Whether the error of an open circuit is wrapped
         * @return The generation of the permit
         * @throws RuntimeException the failure that opened the circuit, or a {@link CircuitOpenException}
         */
        public synchronized long acquire(boolean throwWrappedException) {
            CircuitState current = windowState();
            if (current == CircuitState.OPEN) {
                Throwable cause = lastError;
                if (cause instanceof RuntimeException runtime && !throwWrappedException) {
                    throw runtime;
                }
                throw cause == null ? new CircuitOpenException("Circuit Open") : new CircuitOpenException("Circuit Open: " + cause.getMessage(), cause);
            }
            if (current == CircuitState.HALF_OPEN) {
                if (trials >= Objects.requireNonNull(window).successThreshold()) {
                    throw new CircuitOpenException("Circuit Half-Open: the trial calls are taken");
                }
                trials++;
            }
            return generation;
        }

        /**
         * Return the permit of a call that ended without an outcome, e.g. cancelled: a trial
         * permit of a half-open circuit can be taken again.
         *
         * @param permit The generation of the permit
         */
        public synchronized void release(long permit) {
            if (permit == generation && getState() == CircuitState.HALF_OPEN && trials > 0) {
                trials--;
            }
        }

        /**
         * Record the outcome of a call of a circuit with a window.
         *
         * @param permit     The generation of the permit of the call
         * @param cause      The exception of the call, or {@code null}
         * @param failure    Whether the outcome is a failure
         * @param owner      The user of the circuit, for the events
         * @param retryState The retry state of the call, for the events
         */
        public void record(long permit, @Nullable Throwable cause, boolean failure, CircuitBreakerRetry owner, RetryState retryState) {
            int change;
            Throwable opened;
            synchronized (this) {
                change = recordLocked(permit, failure, cause);
                opened = lastError;
            }
            if (change == 1) {
                owner.publishOpened(retryState, opened == null ? new CircuitOpenException("Circuit Open") : opened);
            } else if (change == -1) {
                owner.publishClosed();
            }
        }

        /**
         * @return 1 if the circuit opened, -1 if it closed, 0 otherwise
         */
        private int recordLocked(long permit, boolean failure, @Nullable Throwable cause) {
            CircuitBreakerWindow w = Objects.requireNonNull(window);
            if (permit != generation) {
                // a call of an older state
                return 0;
            }
            CircuitState current = getState();
            if (current == CircuitState.CLOSED) {
                if (calls == outcomes.length) {
                    if (outcomes[next]) {
                        failures--;
                    }
                } else {
                    calls++;
                }
                outcomes[next] = failure;
                if (failure) {
                    failures++;
                    lastFailure = cause;
                }
                next = (next + 1) % outcomes.length;
                if (calls == outcomes.length && failures >= w.failureThreshold()) {
                    // the success that fills the window opens it with the last failure
                    open(failure ? cause : lastFailure);
                    return 1;
                }
            } else if (current == CircuitState.HALF_OPEN) {
                if (failure) {
                    open(cause);
                    return 1;
                }
                successes++;
                if (successes >= w.successThreshold()) {
                    close();
                    return -1;
                }
            }
            return 0;
        }

        private void open(@Nullable Throwable cause) {
            lastError = cause == null ? new CircuitOpenException("Circuit Open") : cause;
            time = System.currentTimeMillis();
            newGeneration();
            openedCount.incrementAndGet();
            state.set(CircuitState.OPEN);
        }

        private void halfOpen() {
            lastError = null;
            time = System.currentTimeMillis();
            newGeneration();
            state.set(CircuitState.HALF_OPEN);
        }

        private void close() {
            lastError = null;
            time = System.currentTimeMillis();
            newGeneration();
            state.set(CircuitState.CLOSED);
        }

        private void newGeneration() {
            generation++;
            since = System.currentTimeMillis();
            calls = 0;
            failures = 0;
            next = 0;
            trials = 0;
            successes = 0;
            lastFailure = null;
            Arrays.fill(outcomes, false);
        }
    }
}
