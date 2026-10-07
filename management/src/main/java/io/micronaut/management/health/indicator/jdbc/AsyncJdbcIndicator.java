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
package io.micronaut.management.health.indicator.jdbc;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.CompletionStagePublishers;
import io.micronaut.core.util.StringUtils;
import io.micronaut.jdbc.DataSourceResolver;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.aggregator.HealthAggregator;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * The {@link JdbcIndicator} bean, which checks each data source on the blocking executor, and
 * aggregates the results with {@link HealthAggregator#aggregateAsync(String, List)}, without a
 * publisher. It is final, so that a subclass of {@link JdbcIndicator} that overrides
 * {@link #getResult()} is called through it.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
@Requires(beans = HealthEndpoint.class)
@Requires(property = HealthEndpoint.PREFIX + ".jdbc.enabled", notEquals = StringUtils.FALSE)
@Requires(classes = DataSourceResolver.class)
@Requires(beans = DataSource.class)
final class AsyncJdbcIndicator extends JdbcIndicator {

    private final ExecutorService executorService;
    private final DataSource[] dataSources;
    private final DataSourceResolver dataSourceResolver;
    private final HealthAggregator<?> healthAggregator;

    /**
     * @param executorService    The executor service
     * @param dataSources        The data sources
     * @param dataSourceResolver The data source resolver
     * @param healthAggregator   The health aggregator
     */
    AsyncJdbcIndicator(@Named(TaskExecutors.BLOCKING) ExecutorService executorService,
                       DataSource[] dataSources,
                       @Nullable DataSourceResolver dataSourceResolver,
                       HealthAggregator<?> healthAggregator) {
        super(executorService, dataSources, dataSourceResolver, healthAggregator);
        this.executorService = executorService;
        this.dataSources = dataSources;
        this.dataSourceResolver = dataSourceResolver != null ? dataSourceResolver : DataSourceResolver.DEFAULT;
        this.healthAggregator = healthAggregator;
    }

    @Override
    public Publisher<HealthResult> getResult() {
        return CompletionStagePublishers.toPublisher(this::resultAsync);
    }

    @Override
    public CompletionStage<List<HealthResult>> getResultAsync() {
        CompletableFuture<@Nullable HealthResult> result = resultAsync();
        return CompletionStagePublishers.cancelling(result, result.thenApply(r -> r == null ? List.of() : List.of(r)));
    }

    /**
     * @return The aggregated result of the data sources, or {@code null} without data sources
     */
    private CompletableFuture<@Nullable HealthResult> resultAsync() {
        if (dataSources.length == 0) {
            return CompletableFuture.completedFuture(null);
        }
        List<CompletionStage<List<HealthResult>>> stages = new ArrayList<>(dataSources.length);
        for (DataSource dataSource : dataSources) {
            DataSource resolved;
            try {
                resolved = dataSourceResolver.resolve(dataSource);
            } catch (Exception e) {
                stages.add(CompletableFuture.failedFuture(e));
                continue;
            }
            stages.add(check(resolved));
        }
        CompletableFuture<List<HealthResult>> results = CompletionStagePublishers.concat(stages);
        return CompletionStagePublishers.cancelling(results, results.thenCompose(list -> healthAggregator.aggregateAsync(NAME, list)));
    }

    private CompletableFuture<List<HealthResult>> check(DataSource dataSource) {
        try {
            return CompletableFuture.supplyAsync(() -> List.of(checkDataSource(dataSource)), executorService);
        } catch (RejectedExecutionException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
