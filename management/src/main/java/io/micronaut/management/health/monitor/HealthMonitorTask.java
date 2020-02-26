/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.management.health.monitor;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.health.CurrentHealthStatus;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.annotation.Scheduled;
import io.reactivex.Flowable;
import io.reactivex.MaybeObserver;
import io.reactivex.disposables.Disposable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
     * @param healthIndicators    Health indicators
     */
    @Inject
    public HealthMonitorTask(CurrentHealthStatus currentHealthStatus, List<HealthIndicator> healthIndicators) {
        this.currentHealthStatus = currentHealthStatus;
        this.healthIndicators = healthIndicators;
    }

    /**
     * @param currentHealthStatus The current health status
     * @param healthIndicators    Health indicators
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
        List<Publisher<HealthResult>> healthResults = healthIndicators
            .stream()
            .map(HealthIndicator::getResult)
            .collect(Collectors.toList());

        Flowable<HealthResult> resultFlowable = Flowable
            .merge(healthResults)
            .filter(healthResult -> {
                    HealthStatus status = healthResult.getStatus();
                    return status.equals(HealthStatus.DOWN) || !status.getOperational().orElse(true);
                }
            );

        resultFlowable.firstElement().subscribe(new MaybeObserver<HealthResult>() {
            @Override
            public void onSubscribe(Disposable d) {

            }

            @Override
            public void onSuccess(HealthResult healthResult) {
                HealthStatus status = healthResult.getStatus();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Health monitor check failed with status {}", status);
                }
                currentHealthStatus.update(status);
            }

            @Override
            public void onError(Throwable e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Health monitor check failed with exception: " + e.getMessage(), e);
                }

                currentHealthStatus.update(HealthStatus.DOWN.describe("Error occurred running health check: " + e.getMessage()));
            }

            @Override
            public void onComplete() {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Health monitor check passed.");
                }

                currentHealthStatus.update(HealthStatus.UP);
            }
        });
    }
}
