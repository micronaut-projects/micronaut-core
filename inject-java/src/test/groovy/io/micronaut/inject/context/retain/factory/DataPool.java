package io.micronaut.inject.context.retain.factory;

/**
 * A connection pool, as a {@code javax.sql.DataSource} is: made by a factory, wrapped by a listener.
 */
public interface DataPool {

    String name();

    boolean isClosed();

    void close();
}
