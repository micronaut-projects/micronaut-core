package io.micronaut.inject.beans.injectionpoints;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

@Singleton
public class DisposableSingletonDependency {

    private boolean destroyed;

    public boolean isDestroyed() {
        return destroyed;
    }

    @PreDestroy
    void close() {
        destroyed = true;
    }
}
