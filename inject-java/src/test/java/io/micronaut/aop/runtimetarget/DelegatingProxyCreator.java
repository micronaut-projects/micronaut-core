package io.micronaut.aop.runtimetarget;

import io.micronaut.aop.runtime.RuntimeProxyCreator;
import io.micronaut.aop.runtime.RuntimeProxyDefinition;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;
import org.jspecify.annotations.NullMarked;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * A proxy of the public methods of a class, each of which calls the target the subclass decides on.
 */
@NullMarked
abstract class DelegatingProxyCreator implements RuntimeProxyCreator {

    private static final AtomicInteger PROXY_COUNT = new AtomicInteger();

    /**
     * @param proxyDefinition The definition of the proxy being created
     * @param <T> The proxy type
     * @return What a call of the proxy asks for its target
     */
    abstract <T> Supplier<T> target(RuntimeProxyDefinition<T> proxyDefinition);

    @Override
    public <T> T createProxy(RuntimeProxyDefinition<T> proxyDefinition) {
        Class<T> targetType = proxyDefinition.proxyBeanDefinition().getBeanType();
        Supplier<T> target = target(proxyDefinition);
        InvocationHandler toTarget = (proxy, method, args) -> {
            try {
                return method.invoke(target.get(), args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };
        try {
            return new ByteBuddy().subclass(targetType)
                .name(targetType.getName() + "$Proxy" + PROXY_COUNT.incrementAndGet())
                .method(ElementMatchers.isDeclaredBy(targetType).and(ElementMatchers.isPublic()))
                .intercept(InvocationHandlerAdapter.of(toTarget))
                .make()
                .load(proxyDefinition.beanContext().getClassLoader(),
                    ClassLoadingStrategy.UsingLookup.of(MethodHandles.privateLookupIn(targetType, MethodHandles.lookup())))
                .getLoaded()
                .getDeclaredConstructor()
                .newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
