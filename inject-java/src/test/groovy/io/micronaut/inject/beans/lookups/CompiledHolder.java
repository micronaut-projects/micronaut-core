package io.micronaut.inject.beans.lookups;

import jakarta.inject.Singleton;

@Singleton
public class CompiledHolder {

    private final LifeCycleDependency dependency;

    public CompiledHolder(LifeCycleDependency dependency) {
        this.dependency = dependency;
    }

    public LifeCycleDependency getDependency() {
        return dependency;
    }
}
