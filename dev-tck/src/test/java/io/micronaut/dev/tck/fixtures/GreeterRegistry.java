package io.micronaut.dev.tck.fixtures;

import io.micronaut.context.BeanContext;
import io.micronaut.context.WatchableBeanContext;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import jakarta.inject.Singleton;

/**
 * A module registry done right: it watches the definitions it holds, so the startup batch of each
 * generation gives it the current one.
 */
@Singleton
public class GreeterRegistry {

    private final BeanContext beanContext;
    private volatile BeanDefinition<Greeter> latest;

    GreeterRegistry(BeanContext beanContext) {
        this.beanContext = beanContext;
        if (beanContext instanceof WatchableBeanContext watchable) {
            watchable.watchDefinitions(Argument.of(Greeter.class), null, change -> {
                for (BeanDefinition<Greeter> definition : change.current()) {
                    latest = definition;
                }
            });
        }
    }

    public Greeter current() {
        BeanDefinition<Greeter> definition = latest;
        return definition == null ? null : beanContext.getBean(definition);
    }
}
