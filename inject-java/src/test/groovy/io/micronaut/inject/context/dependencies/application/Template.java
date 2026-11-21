package io.micronaut.inject.context.dependencies.application;

import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.watch.ReloadingConfigurationWatcher;

/**
 * A bean the context does not hold as a singleton, whose configuration watch asks for it to be recreated.
 */
@Interleaved
@Requires(property = "spec.name", value = "RecreateRunningApplicationSpec")
public class Template {

    public Template(WatchableBeanContext beanContext) {
        beanContext.configuration("template").watchReloading(change -> ReloadingConfigurationWatcher.Outcome.RECREATE);
    }
}
