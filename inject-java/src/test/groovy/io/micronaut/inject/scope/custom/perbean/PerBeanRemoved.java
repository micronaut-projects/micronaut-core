package io.micronaut.inject.scope.custom.perbean;

import jakarta.annotation.PreDestroy;

/**
 * A bean removed from the scope by its definition rather than by its identifier.
 */
@PerBeanScope
public class PerBeanRemoved {

    private boolean destroyed;

    @PreDestroy
    void destroy() {
        destroyed = true;
    }

    public boolean isDestroyed() {
        return destroyed;
    }
}
