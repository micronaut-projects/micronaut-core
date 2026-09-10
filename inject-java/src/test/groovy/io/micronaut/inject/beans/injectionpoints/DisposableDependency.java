package io.micronaut.inject.beans.injectionpoints;

import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;

@Prototype
public class DisposableDependency {

    private boolean destroyed;

    public boolean isDestroyed() {
        return destroyed;
    }

    @PreDestroy
    void close() {
        destroyed = true;
    }
}
