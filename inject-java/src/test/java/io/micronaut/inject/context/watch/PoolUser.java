package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "BeanWatchTest")
public class PoolUser {
    public final Pool pool;

    public PoolUser(Pool pool) {
        this.pool = pool;
    }
}
