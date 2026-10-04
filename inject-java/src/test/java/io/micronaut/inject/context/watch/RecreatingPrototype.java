package io.micronaut.inject.context.watch;

import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.watch.ConfigurationWatcher;

@Prototype
@Requires(property = "spec.name", value = "BeanWatchTest")
public class RecreatingPrototype {
    public RecreatingPrototype(WatchableBeanContext beanContext) {
        // the context holds no prototype to recreate, so the RECREATE this asks for cannot happen
        beanContext.watchConfiguration("protos.main", change -> ConfigurationWatcher.Outcome.RECREATE);
    }
}
