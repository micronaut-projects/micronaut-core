package io.micronaut.reproduce;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;

/**
 * Mirrors the user-guide example: a concrete subclass of
 * io.micronaut.http.client.HttpClientConfiguration that does not
 * override getConnectionPoolConfiguration().
 *
 * If HttpClientConfiguration#getConnectionPoolConfiguration() is declared
 * abstract in the framework (as reported in the issue), this class will
 * fail to compile because it does not implement that method.
 */
@Named("twitter")
@Singleton
public class TwitterHttpClientConfiguration extends HttpClientConfiguration {

    public TwitterHttpClientConfiguration(ApplicationConfiguration configuration) {
        super(configuration);
    }
}
