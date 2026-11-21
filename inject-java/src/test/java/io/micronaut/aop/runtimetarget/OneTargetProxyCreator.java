package io.micronaut.aop.runtimetarget;

import io.micronaut.aop.runtime.RuntimeProxyDefinition;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NullMarked;

import java.util.function.Supplier;

/**
 * A proxy that resolves its one target as it is created, as the proxy of a pooled Python bean does.
 */
@Singleton
@NullMarked
public class OneTargetProxyCreator extends DelegatingProxyCreator {

    @Override
    <T> Supplier<T> target(RuntimeProxyDefinition<T> proxyDefinition) {
        T target = proxyDefinition.targetBean();
        return () -> target;
    }
}
