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
import io.micronaut.retry.CircuitState;
import io.micronaut.retry.RetryStateBuilder;
import io.micronaut.retry.annotation.RetryPredicate;
import io.micronaut.retry.event.CircuitClosedEvent;
import io.micronaut.retry.event.CircuitOpenEvent;
import io.micronaut.retry.exception.CircuitOpenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalDouble;
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

    @Override
    public void close(@Nullable Throwable exception) {
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
     * Opens the circuit.
     *
     * @return The current state
     */
    private CircuitState openCircuit(Throwable cause) {
        if (cause == null) {
            throw new IllegalArgumentException("Exception cause cannot be null");
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Opening Circuit Breaker [{}] due to error: {}", method, cause.getMessage());
        }
        this.childState = (MutableRetryState) retryStateBuilder.build();
        circuit.lastError = cause;
        circuit.time = System.currentTimeMillis();
        try {
            return state.getAndSet(CircuitState.OPEN);
        } finally {
            if (eventPublisher != null) {
                try {
                    eventPublisher.publishEvent(new CircuitOpenEvent(method, childState, cause));
                } catch (Exception e) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Error publishing CircuitOpen event: {}", e.getMessage(), e);
                    }
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
        if (LOG.isDebugEnabled()) {
            LOG.debug("Closing Circuit Breaker [{}]", method);
        }

        circuit.time = System.currentTimeMillis();
        circuit.lastError = null;
        this.childState = (MutableRetryState) retryStateBuilder.build();
        try {
            return state.getAndSet(CircuitState.CLOSED);
        } finally {
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
        this.childState = (MutableRetryState) retryStateBuilder.build();
        return state.getAndSet(CircuitState.HALF_OPEN);
    }

    /**
     * The state of a circuit: closed, open or half-open, with the error that opened it. Several
     * {@link CircuitBreakerRetry} states can share one, e.g. the users of a named circuit
     * breaker, see {@link io.micronaut.retry.CircuitBreakerRegistry}.
     *
     * @since 5.3.0
     */
    @Internal
    public static final class Circuit {
        private final long openTimeout;
        private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
        @Nullable
        private volatile Throwable lastError;
        private volatile long time = System.currentTimeMillis();

        /**
         * @param openTimeout The time the circuit stays open before it half-opens, in millis
         */
        public Circuit(long openTimeout) {
            this.openTimeout = openTimeout;
        }

        /**
         * @return The time the circuit stays open before it half-opens, in millis
         */
        public long getOpenTimeout() {
            return openTimeout;
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
    }
}
