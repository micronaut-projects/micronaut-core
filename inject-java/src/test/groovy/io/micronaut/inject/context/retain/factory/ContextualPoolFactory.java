package io.micronaut.inject.context.retain.factory;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes a pool per configuration as Micronaut SQL's Hikari factory does: it holds the context, and closes the pools
 * it made when it is destroyed.
 */
@Factory
@Requires(property = "spec.name", value = "RetainedFactoryProductSpec")
@Requires(property = "factory-pools.contextual", value = "true")
public class ContextualPoolFactory implements AutoCloseable {
    private final List<DataPool> made = new ArrayList<>();
    private final ApplicationContext applicationContext;

    public ContextualPoolFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Context
    @EachBean(PoolConfig.class)
    public DataPool pool(PoolConfig config) {
        applicationContext.getProperty("factory-pools.metrics", Boolean.class);
        SimplePool pool = new SimplePool(config.name);
        made.add(pool);
        return pool;
    }

    @Override
    @PreDestroy
    public void close() {
        for (DataPool pool : made) {
            pool.close();
        }
    }
}
