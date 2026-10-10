/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.management.health.monitor;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.CompletionStagePublishers;
import io.micronaut.core.util.StringUtils;
import io.micronaut.health.CurrentHealthStatus;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A continuous health monitor that that updates the {@link CurrentHealthStatus} in a background thread.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(beans = EmbeddedServer.class)
@Requires(property = ApplicationConfiguration.APPLICATION_NAME)
@Requires(property = "micronaut.health.monitor.enabled", value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
public class HealthMonitorTask {

    private static final Logger LOG = LoggerFactory.getLogger(HealthMonitorTask.class);

    private final CurrentHealthStatus currentHealthStatus;
    private final List<HealthIndicator> healthIndicators;

    /**
     * @param currentHealthStatus The current health status
     * @param healthIndicators Health indicators
     */
    @Inject
    public HealthMonitorTask(CurrentHealthStatus currentHealthStatus, List<HealthIndicator> healthIndicators) {
        this.currentHealthStatus = currentHealthStatus;
        this.healthIndicators = healthIndicators;
    }

    /**
     * @param currentHealthStatus The current health status
     * @param healthIndicators Health indicators
     */
    public HealthMonitorTask(CurrentHealthStatus currentHealthStatus, HealthIndicator... healthIndicators) {
        this(currentHealthStatus, Arrays.asList(healthIndicators));
    }

    /**
     * Start the continuous health monitor.
     */
    @Scheduled(
        fixedDelay = "${micronaut.health.monitor.interval:1m}",
        initialDelay = "${micronaut.health.monitor.initial-delay:1m}")
    void monitor() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting health monitor check");
        }
        List<CompletionStage<List<HealthResult>>> results = new ArrayList<>(healthIndicators.size());
        for (HealthIndicator healthIndicator : healthIndicators) {
            results.add(resultOf(healthIndicator));
        }

        CompletionStagePublishers.<HealthResult>concat(results)
            .whenComplete((healthResults, throwable) -> {
                if (throwable != null) {
                    onError(CompletionStagePublishers.unwrap(throwable));
                    return;
                }
                try {
                    update(healthResults);
                } catch (RuntimeException e) {
                    onError(e);
                }
            });
    }

    private void update(List<HealthResult> healthResults) {
        if (LOG.isTraceEnabled() || LOG.isDebugEnabled()) {
            healthResults.forEach(healthResult -> {
                var status = healthResult.getStatus();
                var name = healthResult.getName();
                if (LOG.isTraceEnabled()) {
                    var detail = healthResult.getDetails();
                    LOG.trace("Health monitor result for {}: status {}, details {}", name, status, detail != null ? detail : "{}");
                } else if (LOG.isDebugEnabled()) {
                    LOG.debug("Health monitor result for {}: status {}", name, status);
                }
            });
        }
        Optional<HealthResult> firstDown = healthResults.stream()
            .filter(r -> r.getStatus().equals(HealthStatus.DOWN) || !r.getStatus().getOperational().orElse(true))
            .findFirst();
        if (firstDown.isPresent()) {
            currentHealthStatus.update(firstDown.get().getStatus());
        } else {
            currentHealthStatus.update(HealthStatus.UP);
        }
    }

    /**
     * Collects the results of an indicator from {@link HealthIndicator#getResult()}, all of them
     * as the monitor always did. The monitor keeps the publisher rather than
     * {@link HealthIndicator#getResultAsync()}, so that a test double of an indicator that only
     * stubs {@link HealthIndicator#getResult()} keeps working; the built-in indicators adapt
     * their {@link HealthIndicator#getResultAsync()} to the publisher without a reactive library.
     *
     * @param healthIndicator The indicator
     * @return The results of the indicator
     */
    private static CompletableFuture<List<HealthResult>> resultOf(HealthIndicator healthIndicator) {
        try {
            return CompletionStagePublishers.collect(healthIndicator.getResult());
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private void onError(Throwable e) {
        if (LOG.isErrorEnabled()) {
            LOG.error("Health monitor check failed with exception: {}", e.getMessage(), e);
        }
        currentHealthStatus.update(HealthStatus.DOWN.describe("Error occurred running health check: " + e.getMessage()));
    }
}
