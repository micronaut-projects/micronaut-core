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
package io.micronaut.http.client.loadbalance;

import io.micronaut.core.annotation.Internal;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.client.LoadBalancer;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * The state of the outlier detection of a service, see {@link OutlierDetectionConfiguration}:
 * the consecutive failures of each instance, by URI, and until when an instance is ejected.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class OutlierDetector {
    private final OutlierDetectionConfiguration configuration;
    private final LongSupplier clock;
    private final Map<URI, State> states = new ConcurrentHashMap<>();
    /**
     * Guards the ejection of an instance: the count of the ejected instances and the decision
     * to eject one more are one step, so that concurrent failures of different instances
     * cannot eject more than the maximum share together.
     */
    private final Object ejectionLock = new Object();
    /**
     * The URIs of the instances of the last selection: the maximum share of ejected instances
     * is a share of these, and only these are counted as ejected.
     */
    private volatile Set<URI> members = Set.of();

    /**
     * @param configuration The configuration
     */
    public OutlierDetector(OutlierDetectionConfiguration configuration) {
        this(configuration, System::nanoTime);
    }

    /**
     * @param configuration The configuration
     * @param clock         The clock, in nanoseconds
     */
    public OutlierDetector(OutlierDetectionConfiguration configuration, LongSupplier clock) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * The instances that are not ejected. When every instance is ejected, all of them, so that
     * a service whose every instance failed is still tried.
     *
     * @param instances The instances that are up
     * @return The instances to select among
     */
    public List<ServiceInstance> available(List<ServiceInstance> instances) {
        updateMembers(instances);
        if (states.isEmpty()) {
            return instances;
        }
        long now = clock.getAsLong();
        List<ServiceInstance> available = new ArrayList<>(instances.size());
        for (ServiceInstance instance : instances) {
            State state = states.get(instance.getURI());
            if (state == null || !state.isEjected(now)) {
                available.add(instance);
            }
        }
        return available.isEmpty() ? instances : available;
    }

    /**
     * Record the instances of a selection when they changed, and forget the state of the
     * instances that left: an instance that discovery removed must not take a place in the
     * maximum share of ejected instances. A late report for such an instance creates a state
     * again, which is not counted until the instance is a member again.
     */
    private void updateMembers(List<ServiceInstance> instances) {
        Set<URI> current = members;
        if (current.size() == instances.size()) {
            boolean same = true;
            for (ServiceInstance instance : instances) {
                if (!current.contains(instance.getURI())) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return;
            }
        }
        Set<URI> uris = new HashSet<>();
        for (ServiceInstance instance : instances) {
            uris.add(instance.getURI());
        }
        members = uris;
        states.keySet().retainAll(uris);
    }

    /**
     * Whether the instance is ejected now.
     *
     * @param instance The instance
     * @return Whether it is ejected
     */
    public boolean isEjected(ServiceInstance instance) {
        State state = states.get(instance.getURI());
        return state != null && state.isEjected(clock.getAsLong());
    }

    /**
     * Count the outcome of an exchange with an instance.
     *
     * @param instance The instance
     * @param outcome  The outcome
     */
    public void report(ServiceInstance instance, LoadBalancer.Outcome outcome) {
        State state = states.computeIfAbsent(instance.getURI(), uri -> new State());
        long now = clock.getAsLong();
        synchronized (state) {
            switch (outcome) {
                case SUCCESS -> state.success(now);
                case CONNECT_FAILURE, TIMEOUT, RESET -> {
                    state.consecutiveServerErrors = 0;
                    if (++state.consecutiveFailures >= configuration.getConsecutiveFailures()) {
                        eject(state, now);
                    }
                }
                case SERVER_ERROR -> {
                    state.consecutiveFailures = 0;
                    int threshold = configuration.getConsecutiveServerErrors();
                    if (threshold > 0 && ++state.consecutiveServerErrors >= threshold) {
                        eject(state, now);
                    }
                }
                default -> throw new IllegalArgumentException("Unknown outcome " + outcome);
            }
        }
    }

    /**
     * Eject an instance whose failures reached the threshold, unless that would eject more than
     * the maximum share of the instances; the counts are reset either way, so that the next
     * ejection needs the threshold again. Called with the lock of the instance, and takes the
     * ejection lock after it: nothing takes them in the other order.
     */
    private void eject(State state, long now) {
        state.consecutiveFailures = 0;
        state.consecutiveServerErrors = 0;
        synchronized (ejectionLock) {
            Set<URI> current = members;
            int total = current.size();
            if (total > 0) {
                long ejected = 0;
                for (URI uri : current) {
                    State other = states.get(uri);
                    if (other != null && other != state && other.isEjected(now)) {
                        ejected++;
                    }
                }
                if ((ejected + 1) * 100 > (long) configuration.getMaxEjectionPercent() * total) {
                    return;
                }
            }
            state.ejections++;
            long duration = Math.min(
                configuration.getBaseEjectionTime().toNanos() * state.ejections,
                configuration.getMaxEjectionTime().toNanos());
            state.ejectedUntil = now + duration;
            state.ejected = true;
            state.tried = false;
        }
    }

    /**
     * The state of one instance.
     */
    private static final class State {
        int consecutiveFailures;
        int consecutiveServerErrors;
        int ejections;
        /**
         * Whether the instance is ejected until {@link #ejectedUntil}. A flag, since the
         * clock is a {@code nanoTime}-like value that can be of any sign: only the difference
         * of two of its values can be compared. Volatile, like {@link #ejectedUntil}: they are
         * read without the lock of the instance by {@link #available} and by the ejection of
         * another instance.
         */
        volatile boolean ejected;
        volatile long ejectedUntil;
        /**
         * Whether the instance was ejected and is tried again: a success then resets the
         * ejection multiplier.
         */
        boolean tried;

        boolean isEjected(long now) {
            if (ejected && ejectedUntil - now <= 0) {
                ejected = false;
            }
            return ejected;
        }

        void success(long now) {
            consecutiveFailures = 0;
            consecutiveServerErrors = 0;
            if (ejections > 0 && !isEjected(now)) {
                if (tried) {
                    ejections = 0;
                }
                tried = true;
            }
        }
    }
}
