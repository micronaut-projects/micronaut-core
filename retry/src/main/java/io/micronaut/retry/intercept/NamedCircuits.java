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
package io.micronaut.retry.intercept;

import io.micronaut.core.annotation.Internal;
import io.micronaut.retry.CircuitBreakerPolicy;
import io.micronaut.retry.NamedCircuitBreakerConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The circuits of the named circuit breakers, shared by the
 * {@link io.micronaut.retry.CircuitBreakerRegistry} and the methods annotated
 * {@code @CircuitBreaker(name = "...")}.
 *
 * <p>The reset timeout and the rolling window of a circuit are those of its configuration under
 * {@code micronaut.retry.circuit-breakers.<name>}, if any: a user that declares different ones
 * fails. Without a configuration, every user of the name must declare the same ones, the
 * defaults included: a user that disagrees with the one that created the circuit fails, with
 * both named, whichever came first.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
public final class NamedCircuits {

    private final Map<String, NamedCircuitBreakerConfiguration> configurations = new ConcurrentHashMap<>();
    private final Map<String, Entry> circuits = new ConcurrentHashMap<>();

    /**
     * Named circuits without configuration.
     */
    public NamedCircuits() {
        this(List.of());
    }

    /**
     * @param configurations The configured circuit breakers
     */
    @Inject
    public NamedCircuits(List<NamedCircuitBreakerConfiguration> configurations) {
        for (NamedCircuitBreakerConfiguration configuration : configurations) {
            this.configurations.put(configuration.getName(), configuration);
        }
    }

    /**
     * @param name The name
     * @return The configuration of the name, if any
     */
    public @Nullable NamedCircuitBreakerConfiguration findConfiguration(String name) {
        return configurations.get(name);
    }

    /**
     * @param name The name
     * @return The policy of the configuration of the name, or of the defaults of a configuration
     */
    public CircuitBreakerPolicy policy(String name) {
        NamedCircuitBreakerConfiguration configuration = configurations.get(name);
        return (configuration == null ? new NamedCircuitBreakerConfiguration(name) : configuration).toPolicy();
    }

    /**
     * @param name The name
     * @return The circuit of the name, if it exists
     */
    public CircuitBreakerRetry.@Nullable Circuit find(String name) {
        Entry entry = circuits.get(name);
        return entry == null ? null : entry.circuit();
    }

    /**
     * @return The names of the configured circuits and of the circuits in use
     */
    public Set<String> names() {
        Set<String> names = new TreeSet<>(configurations.keySet());
        names.addAll(circuits.keySet());
        return Collections.unmodifiableSet(names);
    }

    /**
     * The circuit of a name, for a user with a policy.
     *
     * @param name          The name
     * @param policy        The policy of the user
     * @param declaresReset Whether the user declares the reset timeout of the policy, rather than
     *                      takes a default; a policy with a window declares it
     * @param user          The user, for the error of a conflict
     * @return The circuit
     * @throws IllegalStateException if the user disagrees with the circuit
     */
    public CircuitBreakerRetry.Circuit join(String name, CircuitBreakerPolicy policy, boolean declaresReset, String user) {
        Objects.requireNonNull(name, "name");
        NamedCircuitBreakerConfiguration configuration = configurations.get(name);
        if (configuration != null) {
            CircuitBreakerPolicy configured = configuration.toPolicy();
            boolean conflict = declaresReset && !configured.getResetTimeout().equals(policy.getResetTimeout())
                || policy.window() != null && !policy.window().equals(configured.window());
            if (conflict) {
                throw new IllegalStateException("The circuit breaker [" + name + "] of " + user + " declares " + describe(policy)
                    + ", but the configuration " + NamedCircuitBreakerConfiguration.PREFIX + "." + name + " has " + describe(configured)
                    + ": remove them from the user, or make them the same");
            }
            return circuits.computeIfAbsent(name, n -> new Entry(configured, "the configuration " + NamedCircuitBreakerConfiguration.PREFIX + "." + n)).circuit();
        }
        Entry entry = circuits.computeIfAbsent(name, n -> new Entry(policy, user));
        if (!entry.resetTimeout().equals(policy.getResetTimeout()) || !Objects.equals(entry.circuit().getWindow(), policy.window())) {
            throw new IllegalStateException("The circuit breaker [" + name + "] of " + user + " has " + describe(policy)
                + ", but " + entry.user() + " created it with " + describe(entry.resetTimeout(), entry.circuit().getWindow())
                + ": the users of one name must agree, or configure them once under " + NamedCircuitBreakerConfiguration.PREFIX + "." + name);
        }
        return entry.circuit();
    }

    private static String describe(CircuitBreakerPolicy policy) {
        return describe(policy.getResetTimeout(), policy.window());
    }

    private static String describe(Duration resetTimeout, CircuitBreakerPolicy.@Nullable Window window) {
        return "the reset " + resetTimeout + " and " + (window == null ? "no rolling window" : "the rolling window " + window);
    }

    /**
     * A named circuit and the user that created it.
     *
     * @param circuit      The circuit
     * @param resetTimeout The reset timeout of the circuit
     * @param user         The user that created it
     */
    private record Entry(CircuitBreakerRetry.Circuit circuit, Duration resetTimeout, String user) {
        Entry(CircuitBreakerPolicy policy, String user) {
            this(new CircuitBreakerRetry.Circuit(policy.getResetTimeout().toMillis(), policy.window()), policy.getResetTimeout(), user);
        }
    }
}
