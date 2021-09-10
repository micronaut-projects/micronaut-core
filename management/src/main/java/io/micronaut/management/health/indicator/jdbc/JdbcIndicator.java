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

import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.AsyncSingleResultPublisher;
import io.micronaut.core.util.StringUtils;
import io.micronaut.health.HealthStatus;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.jdbc.DataSourceResolver;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.aggregator.HealthAggregator;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
    private final Map<String, DataSource> dataSources;
    private final HealthAggregator<?> healthAggregator;

    /**
     * @param executorService    The executor service
     * @param dataSources        The data sources
     * @param dataSourceResolver The data source resolver
     * @param healthAggregator   The health aggregator
     * @deprecated Use {@link #JdbcIndicator(java.util.concurrent.ExecutorService, java.util.Collection, io.micronaut.jdbc.DataSourceResolver, io.micronaut.management.health.aggregator.HealthAggregator)} instead
     */
    @Deprecated
    public JdbcIndicator(@Named(TaskExecutors.IO) ExecutorService executorService,
                         DataSource[] dataSources,
                         @Nullable DataSourceResolver dataSourceResolver,
                         HealthAggregator<?> healthAggregator) {
        this.executorService = executorService;
        Map<String, DataSource> dataSourceMap = new LinkedHashMap<>(dataSources.length);
        for (int i = 0; i < dataSources.length; i++) {
            DataSource dataSource = dataSources[i];
            if (dataSourceResolver != null) {
                dataSource = dataSourceResolver.resolve(dataSource);
            }
            dataSourceMap.put("dataSource " + (i + 1), dataSource);
        }
        this.dataSources = dataSourceMap;
        this.healthAggregator = healthAggregator;
    }

    /**
     * @param executorService    The executor service
     * @param dataSources        The data sources
     * @param dataSourceResolver The data source resolver
     * @param healthAggregator   The health aggregator
     */
    @Inject
    public JdbcIndicator(@Named(TaskExecutors.IO) ExecutorService executorService,
                         Collection<BeanRegistration<DataSource>> dataSources,
                         @Nullable DataSourceResolver dataSourceResolver,
                         HealthAggregator<?> healthAggregator) {
        this.executorService = executorService;
        Map<String, DataSource> dataSourceMap = new LinkedHashMap<>(dataSources.size());
        for (BeanRegistration<DataSource> dataSource : dataSources) {
            DataSource ds = dataSource.bean();
            if (dataSourceResolver != null) {
                ds = dataSourceResolver.resolve(ds);
            }
            final BeanIdentifier id = dataSource.id();
            if (id != null) {
                dataSourceMap.put(id.getName(), ds);
            } else {
                dataSourceMap.put("default", ds);
            }
        }
        this.dataSources = dataSourceMap;
        this.healthAggregator = healthAggregator;
    }

    private Publisher<HealthResult> getResult(Map.Entry<String, DataSource> entry) {
        if (executorService == null) {
            throw new IllegalStateException("I/O ExecutorService is null");
        }
        final DataSource dataSource = entry.getValue();
        final String name = entry.getKey();
        return new AsyncSingleResultPublisher<>(executorService, () -> {
            Optional<Throwable> throwable = Optional.empty();
            Map<String, Object> details = null;
            String url;
            try (Connection connection = dataSource.getConnection()) {
                if (connection.isValid(CONNECTION_TIMEOUT)) {
                    DatabaseMetaData metaData = connection.getMetaData();
                    url = metaData.getURL();
                    details = new LinkedHashMap<>(1);
                    details.put("database", metaData.getDatabaseProductName());
                    details.put("version", metaData.getDatabaseProductVersion());
                    details.put("url", url);
                } else {
                    throw new SQLException("Connection was not valid");
                }
            } catch (SQLException e) {
                throwable = Optional.of(e);
                try {
                    url = dataSource.getClass().getMethod("getUrl").invoke(dataSource).toString();
                } catch (Exception n) {
                    url = dataSource.getClass().getName() + "@" + Integer.toHexString(dataSource.hashCode());
                }
                details = Collections.singletonMap("url", url);
            }

            HealthResult.Builder builder = HealthResult.builder(name);

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
        if (dataSources.size() == 0) {
            return Flux.empty();
        }
        return healthAggregator.aggregate(NAME, Flux.merge(
            dataSources.entrySet().stream()
                    .map(this::getResult).collect(Collectors.toList())
        ));
    }
}
