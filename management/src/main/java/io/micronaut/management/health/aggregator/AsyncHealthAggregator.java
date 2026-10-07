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
package io.micronaut.management.health.aggregator;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.CompletionStagePublishers;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.endpoint.health.HealthLevelOfDetail;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The {@link DefaultHealthAggregator} bean, which combines the
 * {@link HealthIndicator#getResultAsync()} stages of the indicators without a publisher. It is
 * final, so that a subclass of {@link DefaultHealthAggregator} that overrides the publisher
 * methods, or {@link #aggregateResults(HealthIndicator[])}, is called through them.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
@Requires(beans = HealthEndpoint.class)
final class AsyncHealthAggregator extends DefaultHealthAggregator {

    /**
     * @param applicationConfiguration The application configuration.
     */
    AsyncHealthAggregator(ApplicationConfiguration applicationConfiguration) {
        super(applicationConfiguration);
    }

    /**
     * The publisher of {@link #aggregateAsync(HealthIndicator[], HealthLevelOfDetail)}, which
     * calls the indicators on subscription.
     *
     * @param indicators          The health indicators to aggregate.
     * @param healthLevelOfDetail The {@link HealthLevelOfDetail}
     * @return A publisher of the aggregated response
     */
    @Override
    public Publisher<HealthResult> aggregate(HealthIndicator[] indicators, HealthLevelOfDetail healthLevelOfDetail) {
        return CompletionStagePublishers.toPublisher(() -> aggregateAsync(indicators, healthLevelOfDetail));
    }

    /**
     * Combines the {@link HealthIndicator#getResultAsync()} stages of the indicators. The first
     * indicator that fails fails the aggregation, and cancels the others.
     *
     * @param indicators          The health indicators to aggregate.
     * @param healthLevelOfDetail The {@link HealthLevelOfDetail}
     * @return A {@link CompletionStage} completed with the aggregated response
     */
    @Override
    public CompletionStage<HealthResult> aggregateAsync(HealthIndicator[] indicators, HealthLevelOfDetail healthLevelOfDetail) {
        CompletableFuture<List<HealthResult>> results = aggregateResultsAsync(indicators);
        return CompletionStagePublishers.cancelling(results, results.thenApply(list ->
            buildResult(calculateOverallStatus(list), aggregateDetails(list), healthLevelOfDetail)
        ));
    }

    @Override
    public CompletionStage<HealthResult> aggregateAsync(String name, List<HealthResult> results) {
        return CompletableFuture.completedFuture(
            HealthResult.builder(name, calculateOverallStatus(results)).details(aggregateDetails(results)).build()
        );
    }

    /**
     * Collects the {@link HealthIndicator#getResultAsync()} results of the indicators, in the
     * order of the indicators: all the results of an indicator, as the publisher of
     * {@link #aggregateResults(HealthIndicator[])} merges them. The first indicator that fails,
     * or that throws, fails the result with its error, and the stages of the other indicators
     * are cancelled. Cancelling the result cancels the stages.
     *
     * @param indicators An array of {@link HealthIndicator}
     * @return A {@link CompletableFuture} completed with the results from all health indicators
     */
    CompletableFuture<List<HealthResult>> aggregateResultsAsync(HealthIndicator[] indicators) {
        List<CompletionStage<List<HealthResult>>> stages = new ArrayList<>(indicators.length);
        for (HealthIndicator indicator : indicators) {
            stages.add(resultsOf(indicator));
        }
        return CompletionStagePublishers.concat(stages);
    }

    private static CompletionStage<List<HealthResult>> resultsOf(HealthIndicator indicator) {
        try {
            return indicator.getResultAsync();
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
