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
 * CircuitBreakerGuard.Permit permit = guard.acquire(); // throws while the circuit is open
 * try {
 *     Result result = call();
 *     permit.onSuccess();
 * } catch (RuntimeException e) {
 *     permit.onFailure(e);
 *     throw e;
 * } finally {
 *     permit.release(); // no effect once an outcome is reported
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
     * Acquire the circuit for an operation: fail fast while it is open. Without a rolling window,
     * a half-open circuit lets every operation through and the first outcome closes or opens it
     * again; with one, it permits its trial operations only, see
     * {@link CircuitBreakerPolicy.Window}. Report the outcome of the operation with the permit,
     * or release it when there is none.
     *
     * @return The permit of the operation
     * @throws CircuitOpenException while the circuit is open, with the failure that opened it
     * as the cause, or when the trial operations of a half-open circuit are taken
     */
    Permit acquire();

    /**
     * The permit of one operation, to report its outcome, or to release it when the operation
     * ends without one, e.g. it was cancelled. Only the first report of a permit counts. An
     * outcome reported after the circuit changed state, e.g. of an operation that took long,
     * counts for nothing. A trial permit of a half-open circuit that is neither reported nor
     * released is given again once the reset timeout elapses.
     */
    interface Permit {

        /**
         * Report that the operation succeeded.
         */
        void onSuccess();

        /**
         * Report that the operation failed: with a rolling window, the failure counts as the
         * {@link CircuitBreakerPolicy.Window#isFailure(Throwable) failOn and skipOn} of the
         * window decide; without one, it opens the circuit.
         *
         * @param failure The failure
         */
        void onFailure(Throwable failure);

        /**
         * Release the permit without an outcome, e.g. the operation was cancelled before its
         * outcome was known: a trial permit of a half-open circuit can be taken again.
         */
        void release();
    }
}
