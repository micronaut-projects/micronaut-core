package io.micronaut.aop.runtimetarget;

import io.micronaut.aop.runtime.RuntimeProxy;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Inject;

/**
 * A proxied prototype whose target receives a dependency through a method.
 */
@Prototype
@Advised
@RuntimeProxy(value = PerCallTargetProxyCreator.class, proxyTarget = true)
public class InjectingTarget {

    private TargetDependency dependency;

    @Inject
    public void setDependency(TargetDependency dependency) {
        this.dependency = dependency;
    }

    public boolean injected() {
        return dependency != null;
    }
}
