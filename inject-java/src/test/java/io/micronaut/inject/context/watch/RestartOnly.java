package io.micronaut.inject.context.watch;

import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.watch.ReloadingConfigurationWatcher;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "BeanWatchTest")
public class RestartOnly {
    public RestartOnly(WatchableBeanContext beanContext) {
        beanContext.configuration("server.port").watchReloading(change -> ReloadingConfigurationWatcher.Outcome.REQUIRES_RESTART);
    }
}
