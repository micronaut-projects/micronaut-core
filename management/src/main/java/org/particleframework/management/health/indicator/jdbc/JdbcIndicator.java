package org.particleframework.management.health.indicator.jdbc;

import io.reactivex.Flowable;
import org.particleframework.context.annotation.Requires;
import org.particleframework.core.async.publisher.AsyncSingleResultPublisher;
import org.particleframework.health.HealthStatus;
import org.particleframework.management.endpoint.health.HealthEndpoint;
import org.particleframework.management.health.aggregator.HealthAggregator;
import org.particleframework.management.health.indicator.HealthIndicator;
import org.particleframework.management.health.indicator.HealthResult;
import org.particleframework.scheduling.Schedulers;
import org.reactivestreams.Publisher;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Singleton
@Requires(property = "endpoints.health.jdbc.enabled", notEquals = "false")
@Requires(beans = HealthEndpoint.class)
public class JdbcIndicator implements HealthIndicator {

    private static final String NAME = "jdbc";

    private final ExecutorService executorService;
    private final DataSource[] dataSources;
    private final HealthAggregator healthAggregator;

    @Inject
    public JdbcIndicator(@Named(Schedulers.IO) ExecutorService executorService,
                         DataSource[] dataSources,
                         HealthAggregator healthAggregator) {
        this.executorService = executorService;
        this.dataSources = dataSources;
        this.healthAggregator = healthAggregator;
    }

    private Publisher<HealthResult> getResult(DataSource dataSource) {
        if(executorService == null) {
            throw new IllegalStateException("I/O ExecutorService is null");
        }
        return new AsyncSingleResultPublisher<>(executorService, () -> {
            Optional<Throwable> throwable = Optional.empty();
            Map<String, Object> details = null;
            String key;
            try {
                Connection connection = dataSource.getConnection();
                if (connection.isValid(3)) {
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
            } else  {
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
            Arrays.stream(dataSources).map((ds) -> getResult(ds)).collect(Collectors.toList())
        ));
    }

}
