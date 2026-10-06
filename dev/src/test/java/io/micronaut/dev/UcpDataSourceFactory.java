package io.micronaut.dev;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import jakarta.annotation.PreDestroy;
import oracle.ucp.UniversalConnectionPoolException;
import oracle.ucp.admin.UniversalConnectionPoolManagerImpl;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Makes a UCP pool of an in-memory H2 database, through H2's driver, retained across the generations of an application
 * in development mode, and destroys it when the last generation stops.
 */
@Factory
@Requires(property = "spec.name", value = "UcpGenerationMemoryTest")
public class UcpDataSourceFactory {
    public static final String POOL_NAME = "generations";
    public static final AtomicInteger CREATED = new AtomicInteger();
    private final List<String> made = new ArrayList<>();

    @Context
    @Retain(invalidatedBy = "datasources")
    public DataSource dataSource() throws SQLException {
        PoolDataSource pool = PoolDataSourceFactory.getPoolDataSource();
        pool.setConnectionPoolName(POOL_NAME);
        pool.setConnectionFactoryClassName("org.h2.Driver");
        pool.setURL("jdbc:h2:mem:ucp-generations");
        pool.setUser("sa");
        pool.setPassword("");
        // a connection stays open, so that the in-memory database lives as long as the pool
        pool.setInitialPoolSize(1);
        pool.setMinPoolSize(1);
        made.add(POOL_NAME);
        CREATED.incrementAndGet();
        return pool;
    }

    @PreDestroy
    void close() throws UniversalConnectionPoolException {
        for (String name : made) {
            UniversalConnectionPoolManagerImpl.getUniversalConnectionPoolManager().destroyConnectionPool(name);
        }
    }
}
