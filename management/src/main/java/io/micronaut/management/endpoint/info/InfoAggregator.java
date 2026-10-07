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
package io.micronaut.management.endpoint.info;

import io.micronaut.core.async.publisher.CompletionStagePublishers;
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletionStage;

/**
 * <p>Aggregates all registered info sources into a single response.</p>
 * <p>In case of conflicts, priority is set based on the order of info sources {@link io.micronaut.core.order.Ordered}</p>
 *
 * @param <T> The type
 * @author Zachary Klein
 * @since 1.0
 */
public interface InfoAggregator<T> {

    /**
     * Aggregate an array of {@link InfoSource} and return a publisher.
     *
     * @param sources an array of InfoSources
     * @return A {@link Publisher} of <code>T</code>
     */
    Publisher<T> aggregate(InfoSource[] sources);

    /**
     * The {@link CompletionStage} counterpart of {@link #aggregate(InfoSource[])}, which the
     * {@link InfoEndpoint} calls. By default, it adapts the first item emitted by
     * {@link #aggregate(InfoSource[])}, and completes with {@code null} when the publisher
     * completes without one. Cancelling the stage cancels the subscription. An aggregator that
     * overrides this method should combine the {@link InfoSource#getSourceAsync()} stages of the
     * sources, so that the sources that only implement {@link InfoSource#getSource()} keep working.
     *
     * @param sources an array of InfoSources
     * @return A {@link CompletionStage} completed with the aggregated <code>T</code>
     * @since 5.3.0
     */
    default CompletionStage<T> aggregateAsync(InfoSource[] sources) {
        return CompletionStagePublishers.first(aggregate(sources), null);
    }
}
