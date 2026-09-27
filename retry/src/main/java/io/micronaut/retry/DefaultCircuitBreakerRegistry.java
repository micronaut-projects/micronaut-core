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

import io.micronaut.core.annotation.Internal;
import io.micronaut.retry.intercept.CircuitBreakerRetry;
import io.micronaut.retry.intercept.DefaultRetryRunner;
import io.micronaut.retry.intercept.RetryEventEmitter;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The default {@link CircuitBreakerRegistry}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
public final class DefaultCircuitBreakerRegistry implements CircuitBreakerRegistry {

    private static final RetryEventEmitter NO_OP_EVENT_EMITTER = (retryState, exception) -> { };

    private final Map<String, NamedCircuitBreakerConfiguration> configurations = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreakerRetry.Circuit> circuits = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreakerOperations> configured = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreakerGuard> guards = new ConcurrentHashMap<>();
    private final DefaultRetryRunner retryRunner;

    /**
     * @param configurations  The configured circuit breakers
     * @param executorService The scheduler of the delayed retries
     */
    @Inject
    public DefaultCircuitBreakerRegistry(List<NamedCircuitBreakerConfiguration> configurations,
                                         @Named(TaskExecutors.SCHEDULED) ExecutorService executorService) {
        for (NamedCircuitBreakerConfiguration configuration : configurations) {
            this.configurations.put(configuration.getName(), configuration);
        }
        this.retryRunner = new DefaultRetryRunner((ScheduledExecutorService) executorService, Thread::sleep);
    }

    @Override
    public CircuitBreakerOperations circuitBreaker(String name) {
        Objects.requireNonNull(name, "name");
        return configured.computeIfAbsent(name, n -> {
            NamedCircuitBreakerConfiguration configuration = configurations.get(n);
            CircuitBreakerPolicy policy = configuration == null ? CircuitBreakerPolicy.builder().build() : configuration.toPolicy();
            return operations(n, policy);
        });
    }

    @Override
    public CircuitBreakerOperations circuitBreaker(String name, CircuitBreakerPolicy policy) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(policy, "policy");
        return operations(name, policy);
    }

    private CircuitBreakerOperations operations(String name, CircuitBreakerPolicy policy) {
        return new DefaultCircuitBreakerOperations(policy, sharedCircuit(name, policy), name, retryRunner, NO_OP_EVENT_EMITTER);
    }

    /**
     * The circuit of a name: the existing one, or a new one with the reset timeout and the
     * rolling window of the configuration of the name, or else of the given policy.
     *
     * @param name   The name
     * @param policy The policy of a new circuit that is not configured
     * @return The circuit
     */
    public CircuitBreakerRetry.Circuit sharedCircuit(String name, CircuitBreakerPolicy policy) {
        return circuits.computeIfAbsent(name, n -> {
            NamedCircuitBreakerConfiguration configuration = configurations.get(n);
            CircuitBreakerPolicy circuitPolicy = configuration == null ? policy : configuration.toPolicy();
            return new CircuitBreakerRetry.Circuit(circuitPolicy.getResetTimeout().toMillis(), circuitPolicy.window());
        });
    }

    @Override
    public CircuitBreakerGuard guard(String name) {
        Objects.requireNonNull(name, "name");
        return guards.computeIfAbsent(name, n -> new DefaultCircuitBreakerGuard(n, sharedCircuit(n, CircuitBreakerPolicy.builder().build())));
    }

    @Override
    public Optional<CircuitState> findState(String name) {
        CircuitBreakerRetry.Circuit circuit = circuits.get(name);
        if (circuit == null) {
            return configurations.containsKey(name) ? Optional.of(CircuitState.CLOSED) : Optional.empty();
        }
        CircuitState state = circuit.getState();
        if (state == CircuitState.OPEN && circuit.hasOpenTimeoutElapsed()) {
            return Optional.of(CircuitState.HALF_OPEN);
        }
        return Optional.of(state);
    }

    @Override
    public Optional<CircuitBreakerSnapshot> findSnapshot(String name) {
        CircuitBreakerRetry.Circuit circuit = circuits.get(name);
        if (circuit == null) {
            NamedCircuitBreakerConfiguration configuration = configurations.get(name);
            if (configuration == null) {
                return Optional.empty();
            }
            CircuitBreakerPolicy.Window window = configuration.toPolicy().window();
            return Optional.of(new CircuitBreakerSnapshot(name, CircuitState.CLOSED, window == null ? 0 : window.requestVolumeThreshold(),
                0, 0, 0, 0, 0, null));
        }
        CircuitState state = findState(name).orElse(CircuitState.CLOSED);
        long[] counters = circuit.counters();
        CircuitBreakerPolicy.Window window = circuit.getWindow();
        return Optional.of(new CircuitBreakerSnapshot(name, state, window == null ? 0 : window.requestVolumeThreshold(),
            (int) counters[0], (int) counters[1], (int) counters[2], (int) counters[3], counters[4], Instant.ofEpochMilli(counters[5])));
    }

    @Override
    public Set<String> getNames() {
        Set<String> names = new TreeSet<>(configurations.keySet());
        names.addAll(circuits.keySet());
        return Collections.unmodifiableSet(names);
    }
}
