package io.micronaut.dev;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PreDestroy;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Makes the data source as a connection pool library's factory does, closing it when the factory is destroyed, but
 * holding nothing of the context: retained with the data source, it closes it once the last generation stops.
 */
@Factory
@Requires(property = "spec.name", value = "DataSourceGenerationMemoryTest")
public class PooledDataSourceFactory {
    private final List<PooledDataSource> made = new ArrayList<>();

    @Context
    public DataSource dataSource() {
        PooledDataSource pool = new PooledDataSource("jdbc:h2:mem:generations");
        made.add(pool);
        return pool;
    }

    @PreDestroy
    void close() throws SQLException {
        for (PooledDataSource pool : made) {
            pool.close();
        }
    }
}
