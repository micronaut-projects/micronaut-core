package io.micronaut.inject.context.watch;

import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.watch.ReloadingConfigurationWatcher;
import jakarta.inject.Singleton;

@Factory
@Requires(property = "spec.name", value = "BeanWatchTest")
public class PoolFactory {

    @Singleton
    @Bean
    public Pool pool(WatchableBeanContext beanContext) {
        Pool pool = new Pool();
        // registered while the pool is created: the watch belongs to the pool, so RECREATE replaces the pool
        pool.watch = beanContext.configuration("pools.main").watchReloading(change -> {
            if (change.touches("pools.main.url")) {
                return ReloadingConfigurationWatcher.Outcome.RECREATE;
            }
            if (change.touches("pools.main.password")) {
                pool.applied++;
                return ReloadingConfigurationWatcher.Outcome.APPLIED;
            }
            return ReloadingConfigurationWatcher.Outcome.IGNORED;
        });
        return pool;
    }
}
