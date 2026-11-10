package io.micronaut.inject.context.dependencies.application;

import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.watch.ReloadingConfigurationWatcher;
import jakarta.inject.Singleton;

/**
 * A bean the server receives, as a codec is, whose configuration watch asks for it to be recreated.
 */
@Singleton
@Requires(property = "spec.name", value = "RecreateRunningApplicationSpec")
public class Codec {

    public Codec(WatchableBeanContext beanContext) {
        beanContext.configuration("codec").watchReloading(change -> ReloadingConfigurationWatcher.Outcome.RECREATE);
    }
}
