package io.micronaut.inject.context.retain.factory;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "RetainedFactoryProductSpec")
public class PoolUser {
    public final DataPool pool;

    public PoolUser(@Named("main") DataPool pool) {
        this.pool = pool;
    }
}
