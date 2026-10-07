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
package io.micronaut.management.endpoint.info.impl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.EmptyPropertySource;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.CompletionStagePublishers;
import io.micronaut.management.endpoint.info.InfoEndpoint;
import io.micronaut.management.endpoint.info.InfoSource;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The {@link ReactiveInfoAggregator} bean, which combines the {@link InfoSource#getSourceAsync()}
 * stages of the sources without a publisher. It is final, so that a subclass of
 * {@link ReactiveInfoAggregator} that overrides {@link #aggregate(InfoSource[])} or
 * {@link #aggregateResults(InfoSource[])} is called through them.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
@Requires(beans = InfoEndpoint.class)
final class AsyncInfoAggregator extends ReactiveInfoAggregator {

    /**
     * The publisher of {@link #aggregateAsync(InfoSource[])}, which calls the sources on
     * subscription.
     *
     * @param sources an array of InfoSources
     * @return A publisher of the aggregated properties
     */
    @Override
    public Publisher<Map<String, Object>> aggregate(InfoSource[] sources) {
        return CompletionStagePublishers.toPublisher(() -> aggregateAsync(sources));
    }

    /**
     * Combines the {@link InfoSource#getSourceAsync()} stages of the sources. The first source
     * that fails fails the aggregation.
     *
     * @param sources an array of InfoSources
     * @return A {@link CompletionStage} completed with the aggregated properties
     */
    @Override
    public CompletionStage<Map<String, Object>> aggregateAsync(InfoSource[] sources) {
        CompletableFuture<List<Map.Entry<Integer, PropertySource>>> results = aggregateResultsAsync(sources);
        return CompletionStagePublishers.cancelling(results, results.thenApply(ReactiveInfoAggregator::toProperties));
    }

    /**
     * Collects the {@link InfoSource#getSourceAsync()} property sources of the sources, each keyed
     * by the index of its source. A source that completes with {@code null} contributes an
     * {@link EmptyPropertySource}. The first source that fails, or that throws, fails the result
     * with its error, and the stages of the other sources are cancelled.
     *
     * @param sources Array of {@link InfoSource}
     * @return A {@link CompletableFuture} completed with the list of {@link java.util.Map.Entry}, where the key is an
     * {@link Integer} and value is the {@link PropertySource} returned by the {@link InfoSource}
     */
    CompletableFuture<List<Map.Entry<Integer, PropertySource>>> aggregateResultsAsync(InfoSource[] sources) {
        List<CompletionStage<List<Map.Entry<Integer, PropertySource>>>> stages = new ArrayList<>(sources.length);
        for (int i = 0; i < sources.length; i++) {
            stages.add(sourceOf(i, sources[i]));
        }
        return CompletionStagePublishers.concat(stages);
    }

    private static CompletableFuture<List<Map.Entry<Integer, PropertySource>>> sourceOf(int index, InfoSource source) {
        CompletableFuture<@Nullable PropertySource> propertySource;
        try {
            propertySource = source.getSourceAsync().toCompletableFuture();
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
        return CompletionStagePublishers.cancelling(propertySource, propertySource.thenApply(ps ->
            List.of(new AbstractMap.SimpleEntry<>(index, ps == null ? new EmptyPropertySource() : ps))
        ));
    }
}
