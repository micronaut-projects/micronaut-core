package io.micronaut.runtime.context.scope.refresh.refresher

import io.micronaut.context.WatchableBeanContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.watch.ReloadingConfigurationWatcher
import jakarta.inject.Singleton

@Factory
@Requires(property = "spec.name", value = "ConfigurationRefresherSpec")
class PoolFactory {

    @Singleton
    Pool pool(PoolConfiguration configuration, WatchableBeanContext beanContext) {
        Pool pool = new Pool(configuration.url)
        beanContext.configuration("pool").watchReloading({ change ->
            if (change.touches("pool.url")) {
                return ReloadingConfigurationWatcher.Outcome.RECREATE
            }
            pool.applied++
            return ReloadingConfigurationWatcher.Outcome.APPLIED
        } as ReloadingConfigurationWatcher)
        return pool
    }
}
