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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.retry.exception.CircuitOpenException;

/**
 * The circuit of a named circuit breaker, for a caller that runs each operation itself and
 * reports its outcome, see {@link CircuitBreakerRegistry#guard(String)}. It shares the circuit
 * with every other user of the name: a failure it reports opens the circuit for all of them, and
 * a failure they report opens it for this guard.
 *
 * <pre>{@code
 * CircuitBreakerGuard guard = registry.guard("orders");
 * guard.acquire(); // throws CircuitOpenException while the circuit is open
 * try {
 *     Result result = call();
 *     guard.onSuccess();
 * } catch (RuntimeException e) {
 *     guard.onFailure(e);
 *     throw e;
 * }
 * }</pre>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface CircuitBreakerGuard {

    /**
     * @return The name of the circuit breaker
     */
    String getName();

    /**
     * @return The state of the circuit: an open circuit whose reset timeout elapsed is
     * {@link CircuitState#HALF_OPEN}
     */
    CircuitState getState();

    /**
     * Acquire the circuit for an operation: fail fast while it is open. A half-open circuit lets
     * operations through: the first outcome reported closes or opens it again.
     *
     * @throws CircuitOpenException while the circuit is open, with the failure that opened it
     * as the cause
     */
    void acquire();

    /**
     * Report that an operation succeeded: a half-open circuit closes.
     */
    void onSuccess();

    /**
     * Report that an operation failed: the circuit opens, with the failure as the cause of the
     * exceptions of {@link #acquire()} until it half-opens.
     *
     * @param failure The failure
     */
    void onFailure(Throwable failure);
}
