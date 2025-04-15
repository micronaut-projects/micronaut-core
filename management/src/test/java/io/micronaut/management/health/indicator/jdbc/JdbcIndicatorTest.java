package io.micronaut.management.health.indicator.jdbc;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcIndicatorTest {

    @Test
    void jdbcHealthIndicatorViaConfiguration() {
        Map<String, Object> configuration = Map.of(
            "endpoints.health.jdbc.enabled", StringUtils.FALSE,
            "spec.name", "JdbcIndicatorTest");
        try (ApplicationContext context = ApplicationContext.run(configuration)) {
            assertFalse(context.containsBean(JdbcIndicator.class));
        }
        // enabled by default
        configuration = Map.of("spec.name", "JdbcIndicatorTest");
        try (ApplicationContext context = ApplicationContext.run(configuration)) {
            assertTrue(context.containsBean(JdbcIndicator.class));
        }
    }

    @Requires(property = "spec.name", value = "JdbcIndicatorTest")
    @Singleton
    static class DataSourceMock implements DataSource {

        @Override
        public Connection getConnection() throws SQLException {
            return null;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return null;
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {

        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {

        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return null;
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return false;
        }
    }
}
