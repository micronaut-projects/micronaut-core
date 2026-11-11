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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.async.publisher.CompletionStagePublishers;
import io.micronaut.management.endpoint.health.HealthLevelOfDetail;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * <p>Aggregates all registered health indicators into a single response.</p>
 *
 * @param <T> The aggregator type
 * @author James Kleeh
 * @since 1.0
 */
public interface HealthAggregator<T extends HealthResult> {

    /**
     * @param indicators The health indicators to aggregate.
     * @param healthLevelOfDetail The {@link HealthLevelOfDetail}
     * @return An aggregated response.
     */
    Publisher<T> aggregate(HealthIndicator[] indicators, HealthLevelOfDetail healthLevelOfDetail);

    /**
     * @param name The name of the new health result
     * @param results The health results to aggregate.
     * @return An aggregated {@link HealthResult}.
     */
    Publisher<HealthResult> aggregate(String name, Publisher<HealthResult> results);

    /**
     * The {@link CompletionStage} counterpart of {@link #aggregate(HealthIndicator[], HealthLevelOfDetail)},
     * which the health endpoint calls. By default, it adapts the first result emitted by
     * {@link #aggregate(HealthIndicator[], HealthLevelOfDetail)}, and completes with {@code null}
     * when the publisher completes without one. Cancelling the stage cancels the subscription.
     * An aggregator that overrides this method should combine the
     * {@link HealthIndicator#getResultAsync()} stages of the indicators, so that the indicators
     * that only implement {@link HealthIndicator#getResult()} keep working.
     *
     * @param indicators The health indicators to aggregate.
     * @param healthLevelOfDetail The {@link HealthLevelOfDetail}
     * @return A {@link CompletionStage} completed with the aggregated response, or with {@code null} for none
     * @since 5.3.0
     */
    @Experimental
    default CompletionStage<@Nullable T> aggregateAsync(HealthIndicator[] indicators, HealthLevelOfDetail healthLevelOfDetail) {
        return CompletionStagePublishers.first(aggregate(indicators, healthLevelOfDetail), null);
    }

    /**
     * The {@link CompletionStage} counterpart of {@link #aggregate(String, Publisher)}, for an
     * indicator that combines results it already holds, such as one result per data source.
     * By default, it adapts the first result emitted by {@link #aggregate(String, Publisher)}
     * given a publisher of the results, and completes with {@code null} when the publisher
     * completes without one.
     *
     * @param name The name of the new health result
     * @param results The health results to aggregate.
     * @return A {@link CompletionStage} completed with the aggregated {@link HealthResult}, or with {@code null} for none
     * @since 5.3.0
     */
    @Experimental
    default CompletionStage<@Nullable HealthResult> aggregateAsync(String name, List<HealthResult> results) {
        return CompletionStagePublishers.first(aggregate(name, CompletionStagePublishers.fromList(results)), null);
    }
}
