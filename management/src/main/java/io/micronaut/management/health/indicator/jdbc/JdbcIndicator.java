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
package io.micronaut.management.health.indicator.jdbc;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.CompletionStagePublishers;
import io.micronaut.core.util.StringUtils;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.async.publisher.AsyncSingleResultPublisher;
import io.micronaut.health.HealthStatus;
import io.micronaut.jdbc.DataSourceResolver;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.aggregator.HealthAggregator;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * <p>A {@link io.micronaut.management.health.indicator.HealthIndicator} used to display information about the jdbc
 * status.
 *
 * <p>{@link #getResultAsync()} checks the data sources on the blocking executor and aggregates
 * their results without a publisher. A subclass is called through {@link #getResult()} instead,
 * so that its override keeps working.</p>
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
@Requires(beans = HealthEndpoint.class)
@Requires(property = HealthEndpoint.PREFIX + ".jdbc.enabled", notEquals = StringUtils.FALSE)
@Requires(classes = DataSourceResolver.class)
@Requires(beans = DataSource.class)
public class JdbcIndicator implements HealthIndicator {

    private static final String NAME = "jdbc";
    private static final int CONNECTION_TIMEOUT = 3;

    private final ExecutorService executorService;
    private final DataSource[] dataSources;
    private final DataSourceResolver dataSourceResolver;
    private final HealthAggregator<?> healthAggregator;

    /**
     * @param executorService The executor service
     * @param dataSources The data sources
     * @param dataSourceResolver The data source resolver
     * @param healthAggregator The health aggregator
     */
    public JdbcIndicator(@Named(TaskExecutors.BLOCKING) ExecutorService executorService,
                         DataSource[] dataSources,
                         @Nullable DataSourceResolver dataSourceResolver,
                         HealthAggregator<?> healthAggregator) {
        this.executorService = executorService;
        this.dataSources = dataSources;
        this.dataSourceResolver = dataSourceResolver != null ? dataSourceResolver : DataSourceResolver.DEFAULT;
        this.healthAggregator = healthAggregator;
    }

    private Publisher<HealthResult> getResult(DataSource dataSource) {
        if (executorService == null) {
            throw new IllegalStateException("I/O ExecutorService is null");
        }
        return new AsyncSingleResultPublisher<>(executorService, () -> checkDataSource(dataSource));
    }

    /**
     * Checks the data sources on the blocking executor, and aggregates their results with
     * {@link HealthAggregator#aggregateAsync(String, List)}, without a publisher. A subclass is
     * called through {@link #getResult()}.
     *
     * @return A {@link CompletionStage} completed with the aggregated result, or with no result without data sources
     * @since 5.3.0
     */
    @Override
    public CompletionStage<List<HealthResult>> getResultAsync() {
        if (getClass() != JdbcIndicator.class) {
            return HealthIndicator.super.getResultAsync();
        }
        if (dataSources.length == 0) {
            return CompletableFuture.completedFuture(List.of());
        }
        if (executorService == null) {
            throw new IllegalStateException("I/O ExecutorService is null");
        }
        List<CompletionStage<List<HealthResult>>> stages = new ArrayList<>(dataSources.length);
        for (DataSource dataSource : dataSources) {
            stages.add(check(dataSource));
        }
        CompletableFuture<List<HealthResult>> results = CompletionStagePublishers.concat(stages);
        CompletableFuture<@Nullable HealthResult> aggregated = CompletionStagePublishers.compose(results, list -> CompletionStagePublishers.orElse(
            healthAggregator.aggregateAsync(NAME, list),
            // a mock aggregator that only stubs the publisher method returns no stage
            () -> CompletionStagePublishers.first(healthAggregator.aggregate(NAME, CompletionStagePublishers.fromList(list)), null)
        ));
        return CompletionStagePublishers.map(aggregated, result -> result == null ? List.of() : List.of(result));
    }

    private CompletableFuture<List<HealthResult>> check(DataSource dataSource) {
        CompletableFuture<List<HealthResult>> result = CompletionStagePublishers.future();
        try {
            DataSource resolved = dataSourceResolver.resolve(dataSource);
            executorService.execute(() -> {
                if (!result.isDone()) {
                    result.complete(List.of(checkDataSource(resolved)));
                }
            });
        } catch (Exception e) {
            result.completeExceptionally(e);
        }
        return result;
    }

    /**
     * Checks a data source, blocking.
     *
     * @param dataSource The data source
     * @return The result of the data source
     */
    private HealthResult checkDataSource(DataSource dataSource) {
        Optional<Throwable> throwable = Optional.empty();
        Map<String, Object> details = null;
        String key;
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(CONNECTION_TIMEOUT)) {
                DatabaseMetaData metaData = connection.getMetaData();
                key = metaData.getURL();
                details = new LinkedHashMap<>(1);
                details.put("database", metaData.getDatabaseProductName());
                details.put("version", metaData.getDatabaseProductVersion());
            } else {
                throw new SQLException("Connection was not valid");
            }
        } catch (SQLException e) {
            throwable = Optional.of(e);
            try {
                String url = dataSource.getClass().getMethod("getUrl").invoke(dataSource).toString();
                if (url.startsWith("jdbc:")) {
                    url = url.substring(5);
                }
                url = url.replaceFirst(";", "?");
                url = url.replace(";", "&");
                URI uri = new URI(url);
                key = uri.getHost() + ":" + uri.getPort() + uri.getPath();
            } catch (Exception n) {
                key = dataSource.getClass().getName() + "@" + Integer.toHexString(dataSource.hashCode());
            }
        }

        HealthResult.Builder builder = HealthResult.builder(key);
        if (throwable.isPresent()) {
            builder.exception(throwable.get());
            builder.status(HealthStatus.DOWN);
        } else {
            builder.status(HealthStatus.UP);
            builder.details(details);
        }
        return builder.build();
    }

    @Override
    public Publisher<HealthResult> getResult() {
        if (dataSources.length == 0) {
            return Flux.empty();
        }
        return healthAggregator.aggregate(NAME, Flux.merge(
            Arrays.stream(dataSources)
                .map(dataSourceResolver::resolve)
                .map(this::getResult).collect(Collectors.toList())
        ));
    }
}
