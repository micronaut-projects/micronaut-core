package io.micronaut.aop.runtimetarget;

import io.micronaut.aop.runtime.RuntimeProxyDefinition;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A proxy that asks the definition for its target on every call, as the proxy of a Python bean does.
 */
@Singleton
@NullMarked
public class PerCallTargetProxyCreator extends DelegatingProxyCreator {

    final Map<Class<?>, RuntimeProxyDefinition<?>> definitions = new ConcurrentHashMap<>();

    @Override
    <T> Supplier<T> target(RuntimeProxyDefinition<T> proxyDefinition) {
        definitions.put(proxyDefinition.proxyBeanDefinition().getBeanType(), proxyDefinition);
        return proxyDefinition::targetBean;
    }
}
