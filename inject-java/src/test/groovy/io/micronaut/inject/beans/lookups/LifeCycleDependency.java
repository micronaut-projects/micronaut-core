package io.micronaut.inject.beans.lookups;

import io.micronaut.context.LifeCycle;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;

/**
 * A dependent bean that is also a {@link LifeCycle}, so that a test can tell the destruction of a dependent
 * (which leaves {@link #stop()} alone) from the destruction of a bean in its own right (which calls it).
 */
@Prototype
public class LifeCycleDependency implements LifeCycle<LifeCycleDependency> {

    private boolean destroyed;
    private boolean stopped;

    public boolean isDestroyed() {
        return destroyed;
    }

    public boolean isStopped() {
        return stopped;
    }

    @Override
    public boolean isRunning() {
        return !stopped;
    }

    @Override
    public LifeCycleDependency stop() {
        stopped = true;
        return this;
    }

    @PreDestroy
    void preDestroy() {
        destroyed = true;
    }
}
