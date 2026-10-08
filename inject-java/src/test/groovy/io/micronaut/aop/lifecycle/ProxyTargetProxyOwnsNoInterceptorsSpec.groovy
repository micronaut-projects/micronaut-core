package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext
import spock.lang.Unroll

/**
 * A proxy that intercepts each target with the target's own interceptors is injected with no interceptors: it reports
 * no interceptor registrations of its own and no instance of a non-singleton interceptor is created for it. A proxy
 * that resolves its interceptors once, as it is constructed, keeps the ones it was injected with.
 */
class ProxyTargetProxyOwnsNoInterceptorsSpec extends AbstractTypeElementSpec {

    private static String source(String around) {
        """
package pertarget;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.AROUND)
@interface Probed {
}

class Events {
    static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());
    static final AtomicInteger PROTOTYPES = new AtomicInteger();
    static final AtomicInteger SINGLETONS = new AtomicInteger();
}

@Prototype
@InterceptorBinding(value = Probed.class, kind = InterceptorKind.AROUND)
class PrototypeInterceptor implements MethodInterceptor<Object, Object> {
    final int id = Events.PROTOTYPES.incrementAndGet();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Events.LOG.add("prototype" + id);
        return context.proceed();
    }
}

@Singleton
@InterceptorBinding(value = Probed.class, kind = InterceptorKind.AROUND)
class SingletonInterceptor implements MethodInterceptor<Object, Object> {
    final int id = Events.SINGLETONS.incrementAndGet();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Events.LOG.add("singleton" + id);
        return context.proceed();
    }
}

@Singleton
$around
@Probed
class MyBean {
    String work() { return "worked"; }
}
"""
    }

    @Unroll
    void 'test a proxy that intercepts each target with its own interceptors is injected with none: #around'() {
        given:
        ApplicationContext context = buildContext('pertarget.MyBean', source(around), true)
        def events = context.classLoader.loadClass('pertarget.Events')
        def proxy = context.getBean(context.classLoader.loadClass('pertarget.MyBean'))

        expect: 'the proxy reports no interceptor registrations of its own'
        proxy instanceof Intercepted
        ((Intercepted) proxy).$interceptorRegistrations().isEmpty()

        when:
        def first = proxy.work()
        def second = proxy.work()

        then: 'the one target is intercepted by one instance of each interceptor, and none was created for the proxy'
        first == 'worked'
        second == 'worked'
        events.PROTOTYPES.get() == 1
        events.SINGLETONS.get() == 1
        (events.LOG as List<String>).sort() == ['prototype1', 'prototype1', 'singleton1', 'singleton1']

        cleanup:
        context.close()

        where:
        around << [
            '@Around(proxyTarget = true, lazyInterceptorsPerTarget = true)',
            '@Around(proxyTarget = true, lazy = true, lazyInterceptorsPerTarget = true)',
            '@Around(proxyTarget = true, lazy = true, cacheableLazyTarget = true, lazyInterceptorsPerTarget = true)',
            '@Around(proxyTarget = true, hotswap = true, lazyInterceptorsPerTarget = true)'
        ]
    }

    void 'test a proxy that resolves its interceptors as it is constructed keeps the ones it was injected with'() {
        given:
        ApplicationContext context = buildContext('pertarget.MyBean', source('@Around(proxyTarget = true)'), true)
        def proxy = context.getBean(context.classLoader.loadClass('pertarget.MyBean'))

        expect:
        ((Intercepted) proxy).$interceptorRegistrations()*.bean*.getClass()*.simpleName.sort() == ['PrototypeInterceptor', 'SingletonInterceptor']

        cleanup:
        context.close()
    }
}
