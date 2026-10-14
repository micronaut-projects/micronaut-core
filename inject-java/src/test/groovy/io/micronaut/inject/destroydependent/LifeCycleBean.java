package io.micronaut.inject.destroydependent;

import io.micronaut.context.LifeCycle;
import io.micronaut.context.annotation.Prototype;

/**
 * A life cycle bean without a pre-destroy method of its own: only the context stops it.
 */
@Prototype
public class LifeCycleBean implements LifeCycle<LifeCycleBean> {

    static int stopped;

    final NestedDependency dependency;

    public LifeCycleBean(NestedDependency dependency) {
        this.dependency = dependency;
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public LifeCycleBean stop() {
        stopped++;
        return this;
    }
}
