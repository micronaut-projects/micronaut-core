package io.micronaut.inject.context.retain.factory;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;

/**
 * Makes a pool per configuration, holding nothing of the context: each pool is closed as its own bean is destroyed.
 */
@Factory
@Requires(property = "spec.name", value = "RetainedFactoryProductSpec")
@Requires(property = "factory-pools.contextual", notEquals = "true")
public class PoolFactory {

    @Context
    @EachBean(PoolConfig.class)
    @Bean(preDestroy = "close")
    public DataPool pool(PoolConfig config) {
        return new SimplePool(config.name);
    }
}
