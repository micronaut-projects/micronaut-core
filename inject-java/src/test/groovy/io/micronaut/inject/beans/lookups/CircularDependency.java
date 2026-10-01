package io.micronaut.inject.beans.lookups;

import io.micronaut.context.annotation.Prototype;

/**
 * A compiled bean that can only be created by creating the runtime built {@link LookupHolder}, so that a
 * creator looking this up depends on the bean it is creating.
 */
@Prototype
public class CircularDependency {

    private final LookupHolder holder;

    public CircularDependency(LookupHolder holder) {
        this.holder = holder;
    }

    public LookupHolder getHolder() {
        return holder;
    }
}
