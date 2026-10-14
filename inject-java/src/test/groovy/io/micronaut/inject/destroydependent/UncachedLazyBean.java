package io.micronaut.inject.destroydependent;

import io.micronaut.context.annotation.Prototype;

@UncachedLazy
@Prototype
public class UncachedLazyBean {

    private final BackReference reference;

    public UncachedLazyBean(BackReference reference) {
        this.reference = reference;
        reference.owner = this;
    }

    /**
     * @return The target the call was made on, which is what its dependent references
     */
    public Object target() {
        return reference.owner;
    }
}
