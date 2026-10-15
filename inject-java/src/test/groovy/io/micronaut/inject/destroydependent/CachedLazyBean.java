package io.micronaut.inject.destroydependent;

import io.micronaut.context.annotation.Prototype;

@CachedLazy
@Prototype
public class CachedLazyBean {

    private final TrackedDependency dependency;

    public CachedLazyBean(TrackedDependency dependency) {
        this.dependency = dependency;
    }

    public TrackedDependency dependency() {
        return dependency;
    }
}
