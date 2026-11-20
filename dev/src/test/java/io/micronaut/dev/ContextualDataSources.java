package io.micronaut.dev;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * Wraps the data source as Micronaut Data's {@code ContextualAwareDataSource} does: the wrapper is the bean the context
 * registers, and it holds the context's bean locator.
 */
@Singleton
@Requires(property = "spec.name", value = "DataSourceGenerationMemoryTest")
public final class ContextualDataSources implements BeanCreatedEventListener<DataSource> {
    private final BeanLocator beanLocator;

    public ContextualDataSources(BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    @Override
    public DataSource onCreated(BeanCreatedEvent<DataSource> event) {
        return new Contextual(event.getBean());
    }

    /**
     * The registered data source: the pool, behind a wrapper bound to the context.
     */
    public final class Contextual implements DataSource {
        public final DataSource target;

        Contextual(DataSource target) {
            this.target = target;
        }

        public BeanLocator locator() {
            return beanLocator;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return target.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return target.getConnection(username, password);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return target.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            target.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            target.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return target.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return target.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return target.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return target.isWrapperFor(iface);
        }
    }
}
