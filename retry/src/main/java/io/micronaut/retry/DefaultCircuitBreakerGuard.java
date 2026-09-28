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
package io.micronaut.retry;

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.retry.intercept.CircuitBreakerRetry;
import io.micronaut.retry.intercept.PolicyRetryStateBuilder;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The default {@link CircuitBreakerGuard}: a {@link CircuitBreakerRetry} over the shared circuit,
 * whose retries are never used.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultCircuitBreakerGuard implements CircuitBreakerGuard {

    private final String name;
    private final CircuitBreakerRetry retryState;

    DefaultCircuitBreakerGuard(String name, CircuitBreakerRetry.Circuit circuit, @Nullable ApplicationEventPublisher eventPublisher) {
        this.name = name;
        this.retryState = new CircuitBreakerRetry(
            circuit,
            new PolicyRetryStateBuilder(RetryPolicy.builder().build()),
            new ProgrammaticExecutableMethod(name),
            eventPublisher,
            true
        );
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public CircuitState getState() {
        CircuitState state = retryState.currentState();
        return state == null ? CircuitState.CLOSED : state;
    }

    @Override
    public Permit acquire() {
        CircuitBreakerRetry.Circuit circuit = retryState.circuit();
        CircuitBreakerPolicy.Window window = circuit.getWindow();
        if (window == null) {
            retryState.open();
            return new ReportOnce() {
                @Override
                void success() {
                    retryState.close(null);
                }

                @Override
                void failure(Throwable failure) {
                    retryState.close(failure);
                }

                @Override
                void released() {
                    // a circuit without a window has no permits
                }
            };
        }
        long generation = circuit.acquire(true);
        return new ReportOnce() {
            @Override
            void success() {
                circuit.record(generation, null, false, retryState, retryState);
            }

            @Override
            void failure(Throwable failure) {
                circuit.record(generation, failure, window.isFailure(failure), retryState, retryState);
            }

            @Override
            void released() {
                circuit.release(generation);
            }
        };
    }

    @Override
    public String toString() {
        return "CircuitBreakerGuard{" + name + ", " + getState() + '}';
    }

    /**
     * A permit that reports its first outcome, or its release, and ignores the others.
     */
    private abstract static class ReportOnce implements Permit {

        private final AtomicBoolean reported = new AtomicBoolean();

        @Override
        public final void onSuccess() {
            if (reported.compareAndSet(false, true)) {
                success();
            }
        }

        @Override
        public final void onFailure(Throwable failure) {
            Objects.requireNonNull(failure, "failure");
            if (reported.compareAndSet(false, true)) {
                failure(failure);
            }
        }

        @Override
        public final void release() {
            if (reported.compareAndSet(false, true)) {
                released();
            }
        }

        abstract void success();

        abstract void failure(Throwable failure);

        abstract void released();
    }
}
