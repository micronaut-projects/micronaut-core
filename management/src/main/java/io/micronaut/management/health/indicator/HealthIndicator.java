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
package io.micronaut.management.health.indicator;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.async.publisher.CompletionStagePublishers;
import io.micronaut.core.order.Ordered;
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * <p>Describes an indicator of health of the application. Used by the
 * {@link io.micronaut.management.health.aggregator.HealthAggregator} to create a response combining all indicators.</p>
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface HealthIndicator extends Ordered {

    /**
     * @return A publisher that returns a {@link HealthResult} that provides the
     * information necessary to build a response.
     */
    Publisher<HealthResult> getResult();

    /**
     * The {@link CompletionStage} counterpart of {@link #getResult()}, which the
     * {@link io.micronaut.management.health.aggregator.HealthAggregator} calls. By default, it
     * collects all the {@link HealthResult}s emitted by {@link #getResult()}, as the aggregator
     * always merged them: an indicator may emit several results, or none, in which case it
     * contributes no result. Cancelling the stage cancels the subscription.
     * An indicator that computes its results without a publisher overrides this method. A
     * built-in indicator that does calls a subclass through {@link #getResult()}, so that its
     * override keeps working.
     *
     * <p>An implementation returns a new stage for each call, which a caller may cancel. The
     * framework never cancels a stage it did not create: it ignores its result instead.</p>
     *
     * @return A {@link CompletionStage} completed with the {@link HealthResult}s, in the order they were emitted
     * @since 5.3.0
     */
    @Experimental
    default CompletionStage<List<HealthResult>> getResultAsync() {
        return CompletionStagePublishers.collect(getResult());
    }
}
