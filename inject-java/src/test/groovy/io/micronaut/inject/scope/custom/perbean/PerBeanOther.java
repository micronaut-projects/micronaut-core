package io.micronaut.inject.scope.custom.perbean;

import jakarta.annotation.PreDestroy;

@PerBeanScope
public class PerBeanOther {

    private boolean destroyed;

    @PreDestroy
    void destroy() {
        destroyed = true;
    }

    public boolean isDestroyed() {
        return destroyed;
    }
}
