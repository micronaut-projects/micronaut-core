package io.micronaut.inject.context.retain.listener;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;

/**
 * An application's listener that configures the pool, as the configuration names, and returns it unchanged.
 */
@Singleton
@Requires(property = "spec.name", value = "RetainedListenerSpec")
@Requires(property = "pool.configurer")
public class PoolConfigurer implements BeanCreatedEventListener<ListenedPool> {
    private final String name;

    PoolConfigurer(@Value("${pool.configurer}") String name) {
        this.name = name;
    }

    @Override
    public ListenedPool onCreated(BeanCreatedEvent<ListenedPool> event) {
        event.getBean().configuredBy.add(name);
        return event.getBean();
    }
}
