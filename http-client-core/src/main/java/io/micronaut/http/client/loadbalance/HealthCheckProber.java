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
import io.micronaut.scheduling.TaskScheduler;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The active health check of the instances of a service: probes each instance of the current
 * instance list, static or discovered, at a jittered interval, and reports the results to the
 * {@link OutlierDetector} of the load balancer, so that a probed-unhealthy instance is left out
 * of the selection through the same mechanism as an ejected one.
 *
 * <p>Each round reads the instance list again: a new instance is probed from the next round, the
 * probe results of an instance that left the list are forgotten. A round probes at most
 * {@link Settings#concurrency()} instances at once; a probe that does not complete within
 * {@link Settings#timeout()} fails.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class HealthCheckProber implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(HealthCheckProber.class);

    private final String serviceId;
    private final Settings settings;
    private final Supplier<Publisher<List<ServiceInstance>>> instances;
    private final Function<URI, Publisher<Boolean>> probe;
    private final OutlierDetector detector;
    private final @Nullable TaskScheduler scheduler;
    private volatile boolean closed;
    private volatile @Nullable ScheduledFuture<?> next;

    /**
     * @param serviceId The service ID, for logging
     * @param settings  The settings
     * @param instances The current instances of the service, read on each round
     * @param probe     Probes the instance of the given URI: emits whether it is healthy
     * @param detector  The detector of the load balancer, see {@link AbstractRoundRobinLoadBalancer#enableHealthCheck()}
     * @param scheduler The scheduler of the rounds, or {@code null} to only probe on {@link #probeOnce()}
     */
    public HealthCheckProber(String serviceId,
                             Settings settings,
                             Supplier<Publisher<List<ServiceInstance>>> instances,
                             Function<URI, Publisher<Boolean>> probe,
                             OutlierDetector detector,
                             @Nullable TaskScheduler scheduler) {
        this.serviceId = Objects.requireNonNull(serviceId, "serviceId");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.instances = Objects.requireNonNull(instances, "instances");
        this.probe = Objects.requireNonNull(probe, "probe");
        this.detector = Objects.requireNonNull(detector, "detector");
        this.scheduler = scheduler;
    }

    /**
     * Schedule the first round after one jittered interval, and each next round one jittered
     * interval after the previous one completes.
     */
    public void start() {
        scheduleNext();
    }

    private void scheduleNext() {
        TaskScheduler scheduler = this.scheduler;
        if (closed || scheduler == null) {
            return;
        }
        next = scheduler.schedule(nextDelay(), () -> probeOnce().whenComplete((ignored, error) -> scheduleNext()));
    }

    /**
     * @return The interval, spread by up to the jitter fraction either way
     */
    Duration nextDelay() {
        long interval = settings.interval().toNanos();
        double jitter = settings.jitter();
        if (jitter <= 0) {
            return settings.interval();
        }
        double factor = 1 + ThreadLocalRandom.current().nextDouble(-jitter, jitter);
        return Duration.ofNanos(Math.max(1, (long) (interval * factor)));
    }

    /**
     * Probe every current instance once.
     *
     * @return A future completed when every probe of the round completed; never failed
     */
    public CompletableFuture<Void> probeOnce() {
        if (closed) {
            return CompletableFuture.completedFuture(null);
        }
        return Flux.from(instances.get())
            .next()
            .flatMapMany(list -> {
                Set<URI> uris = new HashSet<>();
                for (ServiceInstance instance : list) {
                    uris.add(instance.getURI());
                }
                detector.retainProbed(uris);
                return Flux.fromIterable(uris);
            })
            .flatMap(uri -> Mono.from(probe.apply(uri))
                .timeout(settings.timeout())
                .onErrorResume(e -> {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Health check of {} for service {} failed: {}", uri, serviceId, e.toString());
                    }
                    return Mono.just(false);
                })
                .defaultIfEmpty(false)
                .doOnNext(healthy -> {
                    if (!closed) {
                        detector.reportProbe(uri, healthy, settings.healthyThreshold(), settings.unhealthyThreshold());
                    }
                }), settings.concurrency())
            .onErrorResume(e -> {
                // the instance list could not be read: keep the results of the last round
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Health check could not read the instances of service {}: {}", serviceId, e.toString());
                }
                return Mono.empty();
            })
            .then()
            .toFuture();
    }

    @Override
    public void close() {
        closed = true;
        ScheduledFuture<?> next = this.next;
        if (next != null) {
            next.cancel(false);
        }
    }

    /**
     * The settings of the health check.
     *
     * @param interval           The interval between two rounds
     * @param jitter             The fraction of the interval by which it is spread either way, from {@code 0} to {@code 1}
     * @param timeout            How long a probe may take before it fails
     * @param concurrency        The maximum number of probes of a round at once
     * @param healthyThreshold   The consecutive successful probes that make an unhealthy instance healthy
     * @param unhealthyThreshold The consecutive failed probes that make a healthy instance unhealthy
     */
    public record Settings(Duration interval, double jitter, Duration timeout, int concurrency,
                           int healthyThreshold, int unhealthyThreshold) {
        /**
         * Validates the settings.
         *
         * @param interval           The interval between two rounds
         * @param jitter             The fraction of the interval by which it is spread either way
         * @param timeout            How long a probe may take before it fails
         * @param concurrency        The maximum number of probes of a round at once
         * @param healthyThreshold   The consecutive successful probes that make an unhealthy instance healthy
         * @param unhealthyThreshold The consecutive failed probes that make a healthy instance unhealthy
         */
        public Settings {
            Objects.requireNonNull(interval, "interval");
            Objects.requireNonNull(timeout, "timeout");
            if (interval.isNegative() || interval.isZero()) {
                throw new IllegalArgumentException("interval must be positive");
            }
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            if (jitter < 0 || jitter >= 1) {
                throw new IllegalArgumentException("jitter must be at least 0 and below 1");
            }
            if (concurrency < 1 || healthyThreshold < 1 || unhealthyThreshold < 1) {
                throw new IllegalArgumentException("concurrency and thresholds must be at least 1");
            }
        }
    }
}
