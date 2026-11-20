package io.micronaut.aop.runtimetarget;

import io.micronaut.aop.runtime.RuntimeProxy;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;

/**
 * A proxied prototype with nothing to inject, which runs the probe as its target is initialized.
 */
@Prototype
@Advised
@RuntimeProxy(value = PerCallTargetProxyCreator.class, proxyTarget = true)
public class InitializingTarget {

    @PostConstruct
    public void initialize() {
        TargetProbe.onCreation.run();
    }

    public boolean ready() {
        return true;
    }
}
