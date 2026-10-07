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
package io.micronaut.management.endpoint.info.impl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.EmptyPropertySource;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourcePropertyResolver;
import io.micronaut.core.async.publisher.CompletionStagePublishers;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.management.endpoint.info.InfoAggregator;
import io.micronaut.management.endpoint.info.InfoEndpoint;
import io.micronaut.management.endpoint.info.InfoSource;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * <p>Default implementation of {@link InfoAggregator}.
 *
 * @author James Kleeh
 * @author Zachary Klein
 * @since 1.0
 */
@Singleton
@Requires(beans = InfoEndpoint.class)
public class ReactiveInfoAggregator implements InfoAggregator<Map<String, Object>> {

    @Override
    public Publisher<Map<String, Object>> aggregate(InfoSource[] sources) {
        return CompletionStagePublishers.toPublisher(() -> aggregateAsync(sources));
    }

    /**
     * Combines the {@link InfoSource#getSourceAsync()} stages of the sources without a
     * publisher. The first source that fails fails the aggregation.
     *
     * @param sources an array of InfoSources
     * @return A {@link CompletionStage} completed with the aggregated properties
     * @since 5.3.0
     */
    @Override
    public CompletionStage<Map<String, Object>> aggregateAsync(InfoSource[] sources) {
        CompletableFuture<List<Map.Entry<Integer, PropertySource>>> results = aggregateResultsAsync(sources).toCompletableFuture();
        return CompletionStagePublishers.cancelling(results, results.thenApply(list -> {
            var resolver = new PropertySourcePropertyResolver();
            list.stream()
                .sorted((e1, e2) -> Integer.compare(e2.getKey(), e1.getKey()))
                .forEach(entry -> resolver.addPropertySource(entry.getValue()));
            return resolver.getAllProperties(StringConvention.RAW, MapFormat.MapTransformation.NESTED);
        }));
    }

    /**
     * Create a {@link Flux} of ordered {@link PropertySource} from an array of {@link InfoSource}.
     *
     * @param sources Array of {@link InfoSource}
     * @return An {@link Flux} of {@link java.util.Map.Entry}, where the key is an {@link Integer} and value is the
     * {@link PropertySource} returned by the {@link InfoSource}
     * @deprecated No longer called: {@link #aggregate(InfoSource[])} and
     * {@link #aggregateAsync(InfoSource[])} collect the property sources with
     * {@link #aggregateResultsAsync(InfoSource[])}, override that method instead.
     */
    @Deprecated(since = "5.3.0")
    protected Flux<Map.Entry<Integer, PropertySource>> aggregateResults(InfoSource[] sources) {
        List<Publisher<Map.Entry<Integer, PropertySource>>> publishers = new ArrayList<>(sources.length);
        for (int i = 0; i < sources.length; i++) {
            int index = i;
            Mono<Map.Entry<Integer, PropertySource>> single = Mono.from(sources[i].getSource())
                .defaultIfEmpty(new EmptyPropertySource())
                .map(source -> new AbstractMap.SimpleEntry<>(index, source));
            publishers.add(single.flux());
        }
        return Flux.merge(publishers);
    }

    /**
     * Collects the {@link InfoSource#getSourceAsync()} property sources of the sources, each keyed
     * by the index of its source. A source that completes with {@code null} contributes an
     * {@link EmptyPropertySource}. The first source that fails, or that throws, fails the result
     * with its error, and the stages of the other sources are cancelled.
     *
     * @param sources Array of {@link InfoSource}
     * @return A {@link CompletionStage} completed with the list of {@link java.util.Map.Entry}, where the key is an
     * {@link Integer} and value is the {@link PropertySource} returned by the {@link InfoSource}
     * @since 5.3.0
     */
    protected CompletionStage<List<Map.Entry<Integer, PropertySource>>> aggregateResultsAsync(InfoSource[] sources) {
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
