package io.micronaut.aop.around.concurrenttarget;

import io.micronaut.aop.runtime.RuntimeProxyCreator;
import io.micronaut.aop.runtime.RuntimeProxyDefinition;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * Keeps the proxy definition so that the test can resolve the target through it, as a proxy that resolves its
 * target on every call does.
 */
@Singleton
@Requires(property = "spec.name", value = "ConcurrentProxyTargetTest")
public class CapturingRuntimeProxy implements RuntimeProxyCreator {

    volatile RuntimeProxyDefinition<?> definition;

    @Override
    public <T> T createProxy(RuntimeProxyDefinition<T> proxyDefinition) {
        definition = proxyDefinition;
        return proxyDefinition.targetBean();
    }
}
