package io.micronaut.inject.context.retain.listener;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A pool that bean created listeners configure rather than replace, as channel initializers declare topology through
 * a channel pool.
 */
@Singleton
@Requires(property = "spec.name", value = "RetainedListenerSpec")
public class ListenedPool {
    public final List<String> configuredBy = new CopyOnWriteArrayList<>();
}
