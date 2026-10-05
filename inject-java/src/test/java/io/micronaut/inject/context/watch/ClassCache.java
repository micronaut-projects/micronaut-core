package io.micronaut.inject.context.watch;

import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.watch.BeanWatch;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A cache keyed by class, which forgets what a class change makes stale: its watch is registered while
 * it is created, so it belongs to it.
 */
@Singleton
@Requires(property = "spec.name", value = "BeanWatchTest")
public class ClassCache {
    public final List<ClassChangeEvent> evicted = new CopyOnWriteArrayList<>();
    public final BeanWatch watch;

    public ClassCache(WatchableBeanContext beanContext) {
        watch = beanContext.watchClassChanges(evicted::add);
    }
}
