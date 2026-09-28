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
import io.micronaut.retry.intercept.DefaultRetryRunner;
import io.micronaut.retry.intercept.NamedCircuits;
import io.micronaut.retry.intercept.RetryEventEmitter;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
final class DefaultCircuitBreakerRegistry implements CircuitBreakerRegistry {

    private static final RetryEventEmitter NO_OP_EVENT_EMITTER = (retryState, exception) -> { };

    private final NamedCircuits circuits;
    @Nullable
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, CircuitBreakerOperations> configured = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreakerGuard> guards = new ConcurrentHashMap<>();
    private final DefaultRetryRunner retryRunner;

    /**
     * @param circuits        The named circuits
     * @param eventPublisher  To publish the events of the circuits
     * @param executorService The scheduler of the delayed retries
     */
    @Inject
    DefaultCircuitBreakerRegistry(NamedCircuits circuits,
                                  @Nullable ApplicationEventPublisher eventPublisher,
                                  @Named(TaskExecutors.SCHEDULED) ExecutorService executorService) {
        this.circuits = circuits;
        this.eventPublisher = eventPublisher;
        this.retryRunner = new DefaultRetryRunner((ScheduledExecutorService) executorService, Thread::sleep);
    }

    @Override
    public CircuitBreakerOperations circuitBreaker(String name) {
        Objects.requireNonNull(name, "name");
        return configured.computeIfAbsent(name, n -> operations(n, circuits.policy(n), circuits.window(n), "CircuitBreakerRegistry.circuitBreaker(\"" + n + "\")"));
    }

    @Override
    public CircuitBreakerOperations circuitBreaker(String name, CircuitBreakerPolicy policy) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(policy, "policy");
        return operations(name, policy, null, "CircuitBreakerRegistry.circuitBreaker(\"" + name + "\", policy)");
    }

    @Override
    public CircuitBreakerOperations circuitBreaker(String name, CircuitBreakerPolicy policy, CircuitBreakerWindow window) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(window, "window");
        return operations(name, policy, window, "CircuitBreakerRegistry.circuitBreaker(\"" + name + "\", policy, window)");
    }

    private CircuitBreakerOperations operations(String name, CircuitBreakerPolicy policy, @Nullable CircuitBreakerWindow window, String user) {
        CircuitBreakerRetry.Circuit circuit = circuits.join(name, policy, window, false, user);
        return new DefaultCircuitBreakerOperations(policy, circuit, name, eventPublisher, retryRunner, NO_OP_EVENT_EMITTER);
    }

    @Override
    public CircuitBreakerGuard guard(String name) {
        Objects.requireNonNull(name, "name");
        return guards.computeIfAbsent(name, n -> new DefaultCircuitBreakerGuard(
            n,
            circuits.join(n, circuits.policy(n), circuits.window(n), false, "CircuitBreakerRegistry.guard(\"" + n + "\")"),
            eventPublisher
        ));
    }

    @Override
    public Optional<CircuitState> findState(String name) {
        return findSnapshot(name).map(CircuitBreakerSnapshot::state);
    }

    @Override
    public Optional<CircuitBreakerSnapshot> findSnapshot(String name) {
        CircuitBreakerRetry.Circuit circuit = circuits.find(name);
        if (circuit != null) {
            return Optional.of(circuit.snapshot(name));
        }
        if (circuits.findConfiguration(name) == null) {
            return Optional.empty();
        }
        CircuitBreakerWindow window = circuits.window(name);
        return Optional.of(new CircuitBreakerSnapshot(name, CircuitState.CLOSED, window == null ? 0 : window.requestVolumeThreshold(),
            0, 0, 0, 0, 0, null));
    }

    @Override
    public Set<String> getNames() {
        return circuits.names();
    }
}
