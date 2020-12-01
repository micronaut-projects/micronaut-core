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
import io.micronaut.core.async.publisher.AsyncSingleResultPublisher;
import io.micronaut.core.util.StringUtils;
import io.micronaut.health.HealthStatus;
import io.micronaut.jdbc.DataSourceResolver;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.aggregator.HealthAggregator;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.scheduling.TaskExecutors;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * <p>A {@link io.micronaut.management.health.indicator.HealthIndicator} used to display information about the jdbc
 * status.
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
     * @param executorService    The executor service
     * @param dataSources        The data sources
     * @param dataSourceResolver The data source resolver
     * @param healthAggregator   The health aggregator
     */
    public JdbcIndicator(@Named(TaskExecutors.IO) ExecutorService executorService,
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
        return new AsyncSingleResultPublisher<>(executorService, () -> {
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
                    key = dataSource.getClass().getMethod("getUrl").invoke(dataSource).toString();
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
        });
    }

    @Override
    public Publisher<HealthResult> getResult() {
        if (dataSources.length == 0) {
            return Flowable.empty();
        }
        return healthAggregator.aggregate(NAME, Flowable.merge(
            Arrays.stream(dataSources)
                    .map(dataSourceResolver::resolve)
                    .map(this::getResult).collect(Collectors.toList())
        ));
    }
}
