package io.micronaut.inject.destroydependent;

/**
 * A bean behind a lazy scoped proxy that is constructed with a dependent, which the proxy is constructed with too.
 */
@SharedScope
public class SharedWithDependency {

    final NestedDependency dependency;

    public SharedWithDependency(NestedDependency dependency) {
        this.dependency = dependency;
    }

    public NestedDependency dependency() {
        return dependency;
    }
}
