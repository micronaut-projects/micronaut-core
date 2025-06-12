package io.micronaut.inject.configuration;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("dd")
class MySQLClientConfiguration {

    @ConfigurationBuilder
    protected MyOptions connectOptions = new MyOptions();

    @ConfigurationBuilder
    protected MyOptions poolOptions = new MyOptions();

    protected String uri;

    /**
     * @return The MySQL connection URI.
     */
    public String getUri() {
        return uri;
    }

    /**
     *
     * @return The options for configuring a connection.
     */
    public MyOptions getConnectOptions() {
        return connectOptions;
    }

    /**
     *
     * @return The options for configuring a connection pool.
     */
    public MyOptions getPoolOptions() {
        return poolOptions;
    }
}
