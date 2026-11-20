package io.micronaut.inject.context.retain.observed;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;

/**
 * The configuration of the client factory, under a prefix no retention names, with a nested configuration of its pool.
 */
@ConfigurationProperties("observed.client")
@Requires(property = "spec.name", value = "ObservedConfigurationSpec")
public class ClientSettings {
    private int timeout = 1;

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    /**
     * The nested configuration of the pool.
     */
    @ConfigurationProperties("pool")
    @Requires(property = "spec.name", value = "ObservedConfigurationSpec")
    public static class PoolSettings {
        private int size = 1;

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }
    }
}
