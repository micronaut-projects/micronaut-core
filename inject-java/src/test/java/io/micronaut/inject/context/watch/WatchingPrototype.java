package io.micronaut.inject.context.watch;

import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.reload.ResourceKind;
import io.micronaut.context.watch.BeanWatch;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A prototype nobody destroys, whose watcher does not reference it: the registry must not keep it reachable.
 */
@Prototype
@Requires(property = "spec.name", value = "BeanWatchTest")
public class WatchingPrototype {
    static final AtomicInteger CHANGES = new AtomicInteger();

    static final AtomicInteger RESOURCE_CHANGES = new AtomicInteger();

    public final BeanWatch watch;
    public final BeanWatch resourceWatch;

    public WatchingPrototype(WatchableBeanContext beanContext) {
        watch = beanContext.configuration("protos.watching").watch(change -> CHANGES.incrementAndGet());
        resourceWatch = beanContext.resources(ResourceKind.STATIC).watch(change -> RESOURCE_CHANGES.incrementAndGet());
    }
}
