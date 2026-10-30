package io.micronaut.inject.context.retain.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;

/**
 * More of the pool's configuration, nested under the prefix its retention names: a class.
 */
@ConfigurationProperties("my.pool.limits")
@Requires(property = "spec.name", value = "RetainedConfigurationSpec")
public class PoolLimits {
    private int max = 10;

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        this.max = max;
    }
}
