/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.management.health.aggregator;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.async.publisher.CompletionStagePublishers;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.endpoint.health.HealthLevelOfDetail;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthIndicatorStages;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * <p>Default implementation of {@link HealthAggregator} that creates a {status: , description: (optional) , details: }
 * response. The top level object represents the most severe status found in the provided health results, or
 * {@link HealthStatus#UNKNOWN} if none found. All registered indicators have their own
 * {status: , description: (optional) , details: } object, keyed by the name of the {@link HealthResult} defined inside
 * the details of the top level object.
 * <p>
 * Example:
 * [status: "UP, details: [diskSpace: [status: UP, details: [:]], cpuUsage: ...]]</p>
 * <p>{@link #aggregateAsync(HealthIndicator[], HealthLevelOfDetail)} combines the
 * {@link HealthIndicator#getResultAsync()} stages of the indicators without a publisher. A
 * subclass is called through its publisher methods instead, so that its overrides of
 * {@link #aggregate(HealthIndicator[], HealthLevelOfDetail)} or
 * {@link #aggregateResults(HealthIndicator[])} keep working.</p>
 *
 * @author James Kleeh
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(beans = HealthEndpoint.class)
public class DefaultHealthAggregator implements HealthAggregator<HealthResult> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultHealthAggregator.class);

    private final ApplicationConfiguration applicationConfiguration;

    /**
     * Default constructor.
     *
     * @param applicationConfiguration The application configuration.
     */
    public DefaultHealthAggregator(ApplicationConfiguration applicationConfiguration) {
        this.applicationConfiguration = applicationConfiguration;
    }

    @Override
    public Publisher<HealthResult> aggregate(HealthIndicator[] indicators, HealthLevelOfDetail healthLevelOfDetail) {
        Flux<HealthResult> results = aggregateResults(indicators);
        Mono<HealthResult> result = results.collectList().map(list -> {
            HealthStatus overallStatus = calculateOverallStatus(list);
            return buildResult(overallStatus, aggregateDetails(list), healthLevelOfDetail);
        });
        return result.flux();
    }

    @Override
    public Publisher<HealthResult> aggregate(String name, Publisher<HealthResult> results) {
        Mono<HealthResult> result = Flux.from(results).collectList().map(list -> {
            HealthStatus overallStatus = calculateOverallStatus(list);
            Object details = aggregateDetails(list);
            return HealthResult.builder(name, overallStatus).details(details).build();
        });
        return result.flux();
    }

    /**
     * Combines the {@link HealthIndicator#getResultAsync()} stages of the indicators, in the order
     * of the indicators, without a publisher. The first indicator that fails, or that throws,
     * fails the aggregation. A subclass is called through
     * {@link #aggregate(HealthIndicator[], HealthLevelOfDetail)}.
     *
     * @param indicators          The health indicators to aggregate.
     * @param healthLevelOfDetail The {@link HealthLevelOfDetail}
     * @return A {@link CompletionStage} completed with the aggregated response
     * @since 5.3.0
     */
    @Override
    public CompletionStage<@Nullable HealthResult> aggregateAsync(HealthIndicator[] indicators, HealthLevelOfDetail healthLevelOfDetail) {
        if (getClass() != DefaultHealthAggregator.class) {
            return HealthAggregator.super.aggregateAsync(indicators, healthLevelOfDetail);
        }
        List<CompletionStage<List<HealthResult>>> stages = new ArrayList<>(indicators.length);
        for (HealthIndicator indicator : indicators) {
            stages.add(HealthIndicatorStages.getResult(indicator));
        }
        return CompletionStagePublishers.map(CompletionStagePublishers.<HealthResult>concat(stages), list ->
            buildResult(calculateOverallStatus(list), aggregateDetails(list), healthLevelOfDetail)
        );
    }

    /**
     * Aggregates the results without a publisher. A subclass is called through
     * {@link #aggregate(String, Publisher)}.
     *
     * @param name    The name of the new health result
     * @param results The health results to aggregate.
     * @return A completed {@link CompletionStage}
     * @since 5.3.0
     */
    @Override
    public CompletionStage<@Nullable HealthResult> aggregateAsync(String name, List<HealthResult> results) {
        if (getClass() != DefaultHealthAggregator.class) {
            return HealthAggregator.super.aggregateAsync(name, results);
        }
        return CompletableFuture.completedFuture(
            HealthResult.builder(name, calculateOverallStatus(results)).details(aggregateDetails(results)).build()
        );
    }

    /**
     * @param results A list of {@link HealthResult}
     * @return The calculated overall health status
     */
    protected HealthStatus calculateOverallStatus(List<HealthResult> results) {
        return results.stream()
            .map(HealthResult::getStatus)
            .sorted()
            .distinct()
            .reduce((a, b) -> b)
            .orElse(HealthStatus.UNKNOWN);
    }

    /**
     * @param indicators An array of {@link HealthIndicator}
     * @return The aggregated results from all health indicators
     */
    protected Flux<HealthResult> aggregateResults(HealthIndicator[] indicators) {
        return Flux.merge(
            Arrays.stream(indicators)
                .map(HealthIndicator::getResult)
                .collect(Collectors.toList())
        );
    }

    /**
     * @param results A list of health results
     * @return The aggregated details for the results
     */
    protected Object aggregateDetails(List<HealthResult> results) {
        Map<String, Object> aggregatedDetails = CollectionUtils.newHashMap(results.size());
        results.forEach(r -> {
            var name = r.getName();
            var details = r.getDetails();
            var status = r.getStatus();
            aggregatedDetails.put(name, buildResult(status, details, HealthLevelOfDetail.STATUS_DESCRIPTION_DETAILS));
            if (LOG.isTraceEnabled()) {
                LOG.trace("Health result for {}: status {}, details {}", name, status, details != null ? details : "{}");
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("Health result for {}: status {}", name, status);
            }
        });

        return aggregatedDetails;
    }

    /**
     * @param status A {@link HealthStatus}
     * @param details The health status details
     * @param healthLevelOfDetail The {@link HealthLevelOfDetail}
     * @return A {@link Map} with the results from the health status
     */
    @SuppressWarnings("MagicNumber")
    @NullUnmarked
    protected HealthResult buildResult(HealthStatus status, @Nullable Object details, HealthLevelOfDetail healthLevelOfDetail) {
        if (healthLevelOfDetail == HealthLevelOfDetail.STATUS) {
            return HealthResult.builder(null, status).build();
        }

        return HealthResult.builder(
            applicationConfiguration.getName().orElse(Environment.DEFAULT_NAME),
            status
        ).details(details).build();
    }
}
