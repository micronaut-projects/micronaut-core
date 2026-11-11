package io.micronaut.inject.context.retain.config;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import jakarta.inject.Singleton;

/**
 * Makes pools from configuration beans: one under the prefix its retention names, one under another prefix.
 */
@Factory
@Requires(property = "spec.name", value = "RetainedConfigurationSpec")
public class SizedPools {

    @Singleton
    @Retain(invalidatedBy = "my.pool")
    SizedPool.Covered covered(PoolProperties properties, PoolLimits limits) {
        return new SizedPool.Covered(properties.getSize(), limits.getMax());
    }

    @Singleton
    @Retain(invalidatedBy = "my.pool")
    SizedPool.Uncovered uncovered(OtherProperties properties) {
        return new SizedPool.Uncovered(properties.getSize());
    }
}
