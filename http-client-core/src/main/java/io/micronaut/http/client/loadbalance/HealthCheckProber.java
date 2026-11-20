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
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
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
    private final Function<ServiceInstance, Publisher<Boolean>> probe;
    private final OutlierDetector detector;
    private final @Nullable TaskScheduler scheduler;
    private volatile boolean closed;
    private final AtomicReference<@Nullable ScheduledFuture<?>> next = new AtomicReference<>();
    private final AtomicReference<@Nullable Disposable> round = new AtomicReference<>();

    /**
     * @param serviceId The service ID, for logging
     * @param settings  The settings
     * @param instances The current instances of the service, read on each round
     * @param probe     Probes the given instance: emits whether it is healthy
     * @param detector  The detector of the load balancer, see {@link AbstractRoundRobinLoadBalancer#enableHealthCheck()}
     * @param scheduler The scheduler of the rounds, or {@code null} to only probe on {@link #probeOnce()}
     */
    public HealthCheckProber(String serviceId,
                             Settings settings,
                             Supplier<Publisher<List<ServiceInstance>>> instances,
                             Function<ServiceInstance, Publisher<Boolean>> probe,
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
        next.set(scheduler.schedule(nextDelay(), () -> probeOnce().whenComplete((ignored, error) -> scheduleNext())));
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
     * <p>A lookup of the instances that fails, throws or does not complete within the probe
     * timeout keeps the results of the last round. A probe that throws counts as a failed
     * probe of its instance only. Closing the prober cancels the round.</p>
     *
     * @return A future completed when every probe of the round completed; never failed
     */
    public CompletableFuture<Void> probeOnce() {
        if (closed) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        Disposable disposable = Mono.defer(() -> Mono.from(instances.get()))
            .timeout(settings.timeout())
            .flatMapMany(list -> {
                // one probe per URI: an instance listed twice is probed once
                Map<URI, ServiceInstance> byUri = new LinkedHashMap<>();
                for (ServiceInstance instance : list) {
                    byUri.putIfAbsent(instance.getURI(), instance);
                }
                detector.retainProbed(byUri.keySet());
                return Flux.fromIterable(byUri.values());
            })
            .takeWhile(instance -> !closed)
            .flatMap(instance -> probe(instance), settings.concurrency())
            .onErrorResume(e -> {
                // the instance list could not be read: keep the results of the last round
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Health check could not read the instances of service {}: {}", serviceId, e.toString());
                }
                return Mono.empty();
            })
            .doFinally(signal -> done.complete(null))
            .subscribe();
        round.set(disposable);
        if (closed) {
            disposable.dispose();
        }
        return done;
    }

    private Mono<Boolean> probe(ServiceInstance instance) {
        URI uri = instance.getURI();
        return Mono.defer(() -> Mono.from(probe.apply(instance)))
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
                });
    }

    @Override
    public void close() {
        closed = true;
        ScheduledFuture<?> scheduled = next.getAndSet(null);
        if (scheduled != null) {
            scheduled.cancel(false);
        }
        Disposable running = round.getAndSet(null);
        if (running != null) {
            running.dispose();
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
         * The default jitter, a fraction of the interval.
         */
        static final double DEFAULT_JITTER = 0.1;
        /**
         * The default maximum number of probes of a round at once.
         */
        static final int DEFAULT_CONCURRENCY = 8;
        /**
         * The longest default probe timeout.
         */
        static final Duration MAX_DEFAULT_TIMEOUT = Duration.ofSeconds(10);

        /**
         * The settings of a health check at the given interval: a probe fails after the
         * interval or after 10 seconds, whichever is shorter; one failed probe makes an instance
         * unhealthy and one successful probe healthy again, as the health check of a
         * configured service always did; at most 8 probes run at once; each interval is
         * spread by up to 10% either way.
         *
         * @param interval The interval between two rounds
         * @return The settings
         */
        public static Settings of(Duration interval) {
            Duration timeout = interval.compareTo(MAX_DEFAULT_TIMEOUT) < 0 ? interval : MAX_DEFAULT_TIMEOUT;
            return new Settings(interval, DEFAULT_JITTER, timeout, DEFAULT_CONCURRENCY, 1, 1);
        }

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
            if (!(jitter >= 0 && jitter < 1)) {
                throw new IllegalArgumentException("jitter must be at least 0 and below 1");
            }
            if (concurrency < 1 || healthyThreshold < 1 || unhealthyThreshold < 1) {
                throw new IllegalArgumentException("concurrency and thresholds must be at least 1");
            }
        }
    }
}
