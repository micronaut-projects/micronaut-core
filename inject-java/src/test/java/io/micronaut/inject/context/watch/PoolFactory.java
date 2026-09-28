package io.micronaut.inject.context.watch;

import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.watch.ConfigurationWatcher;
import jakarta.inject.Singleton;

@Factory
@Requires(property = "spec.name", value = "BeanWatchTest")
public class PoolFactory {

    @Singleton
    @Bean
    public Pool pool(WatchableBeanContext beanContext) {
        Pool pool = new Pool();
        // registered while the pool is created: the watch belongs to the pool, so RECREATE replaces the pool
        pool.watch = beanContext.watchConfiguration("pools.main", change -> {
            if (change.touches("pools.main.url")) {
                return ConfigurationWatcher.Outcome.RECREATE;
            }
            if (change.touches("pools.main.password")) {
                pool.applied++;
                return ConfigurationWatcher.Outcome.APPLIED;
            }
            return ConfigurationWatcher.Outcome.IGNORED;
        });
        return pool;
    }
}
