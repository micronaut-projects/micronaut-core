package io.micronaut.dev;

import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * A parent-tier connection pool over an in-memory H2 database, which it keeps open with a connection of its own until
 * it is closed: the database lives as long as the pool.
 */
public final class PooledDataSource implements DataSource, AutoCloseable {
    public static final AtomicInteger CREATED = new AtomicInteger();
    private final JdbcDataSource h2 = new JdbcDataSource();
    private final Connection keeper;
    private volatile boolean closed;

    PooledDataSource(String url) {
        h2.setURL(url);
        try {
            keeper = h2.getConnection();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        CREATED.incrementAndGet();
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() throws SQLException {
        closed = true;
        keeper.close();
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (closed) {
            throw new SQLException("The pool is closed");
        }
        return h2.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnection();
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
    }

    @Override
    public void setLoginTimeout(int seconds) {
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Not a " + iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }
}
