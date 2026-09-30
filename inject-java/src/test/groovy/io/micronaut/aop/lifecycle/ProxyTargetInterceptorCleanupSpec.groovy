package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.HotSwappableInterceptedProxy
import io.micronaut.context.ApplicationContext

import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The interceptors a proxy creates for a target it fronts are destroyed whatever becomes of the selection they were
 * created for: one that fails destroys what it created already, and the interceptors of a target the context holds no
 * registration for, which nothing owns, are destroyed when the context closes.
 */
class ProxyTargetInterceptorCleanupSpec extends AbstractTypeElementSpec {

    private static final String BINDING = '''
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.AROUND)
@interface Probed {
}
'''

    private static final String IMPORTS = '''
import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
'''

    void 'test a selection that fails destroys the interceptors it created already'() {
        given:
        ApplicationContext context = buildContext('failing.MyBean', '''
package failing;
''' + IMPORTS + BINDING + '''
class Events {
    static final AtomicInteger ATTEMPTS = new AtomicInteger();
    static final AtomicInteger CREATED = new AtomicInteger();
    static final AtomicInteger DESTROYED = new AtomicInteger();
    static volatile boolean failSecond = true;

    // the second interceptor of a selection fails to be created, whichever of the two it is
    static void creating() {
        if (ATTEMPTS.incrementAndGet() == 2 && failSecond) {
            throw new IllegalStateException("cannot create the second interceptor");
        }
        CREATED.incrementAndGet();
    }
}

@Prototype
@InterceptorBinding(value = Probed.class, kind = InterceptorKind.AROUND)
class FirstInterceptor implements MethodInterceptor<Object, Object> {
    FirstInterceptor() { Events.creating(); }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) { return context.proceed(); }

    @PreDestroy
    void destroy() { Events.DESTROYED.incrementAndGet(); }
}

@Prototype
@InterceptorBinding(value = Probed.class, kind = InterceptorKind.AROUND)
class SecondInterceptor implements MethodInterceptor<Object, Object> {
    SecondInterceptor() { Events.creating(); }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) { return context.proceed(); }

    @PreDestroy
    void destroy() { Events.DESTROYED.incrementAndGet(); }
}

@Singleton
@Around(proxyTarget = true, lazy = true, lazyInterceptorsPerTarget = true)
@Probed
class MyBean {
    String work() { return "worked"; }
}
''', true)
        def events = context.classLoader.loadClass('failing.Events')
        def proxy = context.getBean(context.classLoader.loadClass('failing.MyBean'))

        when: 'the selection of the first call fails on its second interceptor'
        proxy.work()

        then: 'the one created before the failure is destroyed at once'
        thrown(RuntimeException)
        events.CREATED.get() == 1
        events.DESTROYED.get() == 1

        when: 'the selection is made again'
        events.failSecond = false
        def result = proxy.work()

        then: 'both interceptors are created for the target'
        result == 'worked'
        events.CREATED.get() == 3
        events.DESTROYED.get() == 1

        when:
        context.close()

        then: 'and destroyed with it'
        events.DESTROYED.get() == 3
    }

    private static String unmanaged(String creating) {
        '''
package unmanaged;
''' + IMPORTS + BINDING + '''
class Events {
    static final AtomicInteger CREATED = new AtomicInteger();
    static final AtomicInteger DESTROYED = new AtomicInteger();
    static volatile CyclicBarrier barrier;
}

@Prototype
@InterceptorBinding(value = Probed.class, kind = InterceptorKind.AROUND)
class ProbingInterceptor implements MethodInterceptor<Object, Object> {
    ProbingInterceptor() throws Exception {
        Events.CREATED.incrementAndGet();
''' + creating + '''
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) { return context.proceed(); }

    @PreDestroy
    void destroy() { Events.DESTROYED.incrementAndGet(); }
}

@Singleton
@Around(proxyTarget = true, hotswap = true, lazyInterceptorsPerTarget = true)
@Probed
class MyBean {
    String work() { return "worked"; }
}
'''
    }

    private static Object newTarget(ApplicationContext context) {
        def constructor = context.classLoader.loadClass('unmanaged.MyBean').getDeclaredConstructor()
        constructor.accessible = true
        constructor.newInstance()
    }

    void 'test the interceptors of a swapped in target the context does not manage are destroyed with the context'() {
        given:
        ApplicationContext context = buildContext('unmanaged.MyBean', unmanaged(''), true)
        def events = context.classLoader.loadClass('unmanaged.Events')
        def proxy = context.getBean(context.classLoader.loadClass('unmanaged.MyBean'))

        when: 'the original target is called, then one the context holds no registration for'
        proxy.work()
        ((HotSwappableInterceptedProxy) proxy).swap(newTarget(context))
        proxy.work()
        proxy.work()

        then: 'an interceptor was created for each'
        events.CREATED.get() == 2
        events.DESTROYED.get() == 0

        when:
        context.close()

        then: 'both are destroyed'
        events.DESTROYED.get() == 2
    }

    void 'test the interceptors of a selection for an unmanaged target that another thread made first are destroyed'() {
        given:
        ApplicationContext context = buildContext('unmanaged.MyBean', unmanaged('''
        CyclicBarrier barrier = Events.barrier;
        if (barrier != null) {
            // both threads are selecting before either keeps its selection
            barrier.await(30, TimeUnit.SECONDS);
        }
'''), true)
        def events = context.classLoader.loadClass('unmanaged.Events')
        def proxy = context.getBean(context.classLoader.loadClass('unmanaged.MyBean'))
        def executor = Executors.newFixedThreadPool(2)

        when: 'two threads make the first call of an unmanaged target at once'
        ((HotSwappableInterceptedProxy) proxy).swap(newTarget(context))
        events.barrier = new CyclicBarrier(2)
        def calls = (1..2).collect { executor.submit({ proxy.work() } as java.util.concurrent.Callable) }
        def results = calls.collect { it.get(60, TimeUnit.SECONDS) }
        events.barrier = null

        then: 'each created an interceptor, and the one of the selection that was not kept is destroyed'
        results == ['worked', 'worked']
        events.CREATED.get() == 2
        events.DESTROYED.get() == 1

        when:
        proxy.work()
        context.close()

        then: 'the kept one is destroyed with the context'
        events.CREATED.get() == 2
        events.DESTROYED.get() == 2

        cleanup:
        executor.shutdownNow()
    }
}
