package io.micronaut.inject.context.retain.observed;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import jakarta.inject.Singleton;

/**
 * A bean that received only the nested configuration of the pool, and copied its size.
 */
@Singleton
@Retain
@Requires(property = "spec.name", value = "ObservedConfigurationSpec")
public class PoolUser {
    public final int size;

    PoolUser(ClientSettings.PoolSettings pool) {
        this.size = pool.getSize();
    }
}
