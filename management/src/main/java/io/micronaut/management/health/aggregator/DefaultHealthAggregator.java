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
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

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
        return CompletionStagePublishers.toPublisher(() -> aggregateAsync(indicators, healthLevelOfDetail));
    }

    @Override
    public Publisher<HealthResult> aggregate(String name, Publisher<HealthResult> results) {
        return CompletionStagePublishers.toPublisher(() -> {
            CompletableFuture<List<HealthResult>> collected = CompletionStagePublishers.collect(results);
            return CompletionStagePublishers.cancelling(collected, collected.thenApply(list -> aggregateResult(name, list)));
        });
    }

    /**
     * Combines the {@link HealthIndicator#getResultAsync()} stages of the indicators, without a
     * publisher. The first indicator that fails fails the aggregation, and cancels the others.
     *
     * @param indicators The health indicators to aggregate.
     * @param healthLevelOfDetail The {@link HealthLevelOfDetail}
     * @return A {@link CompletionStage} completed with the aggregated response
     * @since 5.3.0
     */
    @Override
    public CompletionStage<HealthResult> aggregateAsync(HealthIndicator[] indicators, HealthLevelOfDetail healthLevelOfDetail) {
        CompletableFuture<List<HealthResult>> results = aggregateResultsAsync(indicators).toCompletableFuture();
        return CompletionStagePublishers.cancelling(results, results.thenApply(list -> {
            HealthStatus overallStatus = calculateOverallStatus(list);
            return buildResult(overallStatus, aggregateDetails(list), healthLevelOfDetail);
        }));
    }

    @Override
    public CompletionStage<HealthResult> aggregateAsync(String name, List<HealthResult> results) {
        return CompletableFuture.completedFuture(aggregateResult(name, results));
    }

    private HealthResult aggregateResult(String name, List<HealthResult> results) {
        HealthStatus overallStatus = calculateOverallStatus(results);
        Object details = aggregateDetails(results);
        return HealthResult.builder(name, overallStatus).details(details).build();
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
     * @deprecated No longer called: {@link #aggregate(HealthIndicator[], HealthLevelOfDetail)} and
     * {@link #aggregateAsync(HealthIndicator[], HealthLevelOfDetail)} collect the results with
     * {@link #aggregateResultsAsync(HealthIndicator[])}, override that method instead.
     */
    @Deprecated(since = "5.3.0")
    protected Flux<HealthResult> aggregateResults(HealthIndicator[] indicators) {
        return Flux.merge(
            Arrays.stream(indicators)
                .map(HealthIndicator::getResult)
                .collect(Collectors.toList())
        );
    }

    /**
     * Collects the {@link HealthIndicator#getResultAsync()} results of the indicators, in the
     * order of the indicators. An indicator that completes with {@code null} contributes no
     * result. The first indicator that fails, or that throws, fails the result with its error,
     * and the stages of the other indicators are cancelled. Cancelling the result cancels the
     * stages.
     *
     * @param indicators An array of {@link HealthIndicator}
     * @return A {@link CompletionStage} completed with the results from all health indicators
     * @since 5.3.0
     */
    protected CompletionStage<List<HealthResult>> aggregateResultsAsync(HealthIndicator[] indicators) {
        List<CompletionStage<List<HealthResult>>> stages = new ArrayList<>(indicators.length);
        for (HealthIndicator indicator : indicators) {
            stages.add(resultOf(indicator));
        }
        return CompletionStagePublishers.concat(stages);
    }

    /**
     * @param indicator The indicator
     * @return The result of the indicator as a list of at most one result
     */
    private static CompletableFuture<List<HealthResult>> resultOf(HealthIndicator indicator) {
        CompletableFuture<@Nullable HealthResult> result;
        try {
            result = indicator.getResultAsync().toCompletableFuture();
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
        return CompletionStagePublishers.cancelling(result, result.thenApply(r -> r == null ? List.of() : List.of(r)));
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
