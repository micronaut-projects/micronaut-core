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

import java.util.Optional;
import java.util.Set;

/**
 * Circuit breakers by name, shared by everything that uses the same name: the programmatic
 * {@link CircuitBreakerOperations} of this registry, the methods annotated
 * {@code @CircuitBreaker(name = "...")}, and other modules. They open,
 * half-open and close one circuit together: a failure through one of them opens it for all, and
 * while it is open all of them fail fast.
 *
 * <p>A named circuit breaker is configured under {@code micronaut.retry.circuit-breakers.<name>},
 * see {@link NamedCircuitBreakerConfiguration}: its reset timeout is the timeout of the circuit,
 * and its attempts, delays and multiplier are the retries of {@link #circuitBreaker(String)}.
 * Each user of the circuit keeps its own retries: an annotated method those of its annotation,
 * {@link #circuitBreaker(String, CircuitBreakerPolicy)} those of the policy.</p>
 *
 * <p>The reset timeout and the rolling window of a circuit are those of its configuration. A
 * circuit that is not configured has those of its users, which must all agree: those of
 * {@link #circuitBreaker(String)} and {@link #guard(String)} are the defaults of
 * {@link NamedCircuitBreakerConfiguration}. A user that disagrees fails with an
 * {@link IllegalStateException} that names the user that created the circuit.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface CircuitBreakerRegistry {

    /**
     * The circuit breaker of a name, with the retries of its configuration, or of the default
     * {@link CircuitBreakerPolicy} if it is not configured.
     *
     * @param name The name
     * @return The operations over the circuit of the name
     */
    CircuitBreakerOperations circuitBreaker(String name);

    /**
     * The circuit breaker of a name, with the retries of a policy: the circuit is shared with
     * the other users of the name, the retries are these. The reset timeout of the policy is
     * ignored when the circuit is configured; when the circuit is not configured, it must be the
     * one of the other users of the name, which must have no rolling window either.
     *
     * @param name   The name
     * @param policy The policy of the retries, and the reset timeout of the circuit
     * @return The operations over the circuit of the name
     * @throws IllegalStateException if the policy disagrees with the circuit of the name
     */
    CircuitBreakerOperations circuitBreaker(String name, CircuitBreakerPolicy policy);

    /**
     * The circuit breaker of a name, with the retries of a policy, over a circuit with a rolling
     * window. The window must be the one of the configuration when the circuit is configured,
     * whose reset timeout then applies; when the circuit is not configured, the reset timeout of
     * the policy and the window must be those of the other users of the name.
     *
     * @param name   The name
     * @param policy The policy of the retries, and the reset timeout of the circuit
     * @param window The rolling window of the circuit
     * @return The operations over the circuit of the name
     * @throws IllegalStateException if the policy or the window disagree with the circuit of the name
     */
    CircuitBreakerOperations circuitBreaker(String name, CircuitBreakerPolicy policy, CircuitBreakerWindow window);

    /**
     * The guard of the circuit of a name, for a caller that runs the operation itself and
     * reports its outcome, rather than handing it to {@link CircuitBreakerOperations}: it never
     * retries. A caller acquires the guard before each operation, which fails fast while the
     * circuit is open, and reports the outcome once it is known, e.g. an HTTP proxy once the headers
     * of the response arrive, while the body still streams.
     *
     * @param name The name
     * @return The guard of the circuit of the name
     */
    CircuitBreakerGuard guard(String name);

    /**
     * @param name The name
     * @return The state of the circuit of the name, if it exists
     */
    Optional<CircuitState> findState(String name);

    /**
     * @param name The name
     * @return The state and the counters of the circuit of the name, if it exists, e.g. for an
     * endpoint or metrics
     */
    Optional<CircuitBreakerSnapshot> findSnapshot(String name);

    /**
     * @return The names of the circuits that exist: the configured ones and the ones in use
     */
    Set<String> getNames();
}
