package io.micronaut.aop.around.concurrenttarget;

import io.micronaut.aop.runtime.RuntimeProxyCreator;
import io.micronaut.aop.runtime.RuntimeProxyDefinition;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Caches the target while the proxy is created, as an eager proxy does, and keeps the proxy definition so that the
 * test can also resolve the target through it, as a proxy that resolves its target on every call does.
 */
@Singleton
@Requires(property = "spec.name", value = "ConcurrentProxyTargetTest")
public class CapturingRuntimeProxy implements RuntimeProxyCreator {

    volatile @Nullable RuntimeProxyDefinition<?> definition;
    volatile @Nullable Object cachedTarget;

    @Override
    public <T> T createProxy(RuntimeProxyDefinition<T> proxyDefinition) {
        definition = proxyDefinition;
        cachedTarget = proxyDefinition.targetBean();
        // stands in for the proxy
        return (T) new PrototypeService();
    }
}
