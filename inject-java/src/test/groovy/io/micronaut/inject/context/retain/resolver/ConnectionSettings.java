package io.micronaut.inject.context.retain.resolver;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;

/**
 * The connection's configuration, which it resolves through its resolver and copies.
 */
@ConfigurationProperties("resolver.connection")
@Requires(property = "spec.name", value = "RetainedResolverSpec")
public class ConnectionSettings {
    private int timeout = 1;

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}
