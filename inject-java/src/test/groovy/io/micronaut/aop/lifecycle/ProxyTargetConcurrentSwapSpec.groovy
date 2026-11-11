package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.HotSwappableInterceptedProxy
import io.micronaut.aop.InterceptedProxy
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A hot-swappable proxy reads its target and the registration of the target as one: a call that races with a swap
 * intercepts the target it reaches with the interceptors of that target, and never takes a registered target for one
 * the context does not manage.
 */
class ProxyTargetConcurrentSwapSpec extends AbstractTypeElementSpec {

    private static final int CALLS = 3_000_000
    private static final long LIMIT_NANOS = TimeUnit.SECONDS.toNanos(3)

    private static String source(String beans) {
        '''
package swapping;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.AROUND)
@interface Probed {
}

class Events {
    static final AtomicInteger CREATED = new AtomicInteger();
    // the interceptor instances that intercepted each target
    static final Map<Object, Set<Integer>> SEEN = new ConcurrentHashMap<>();
}

@Prototype
@InterceptorBinding(value = Probed.class, kind = InterceptorKind.AROUND)
class ProbingInterceptor implements MethodInterceptor<Object, Object> {
    final int id = Events.CREATED.incrementAndGet();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Events.SEEN.computeIfAbsent(((MyBean) context.getTarget()).name, name -> ConcurrentHashMap.newKeySet()).add(id);
        return context.proceed();
    }
}
''' + beans
    }

    /**
     * Calls the proxy on this thread while another one runs the given action over and over.
     */
    private static void callWhile(Object proxy, Object events, int expectedInterceptors, Closure<?> action) {
        def stop = new AtomicBoolean()
        def executor = Executors.newSingleThreadExecutor()
        def other = executor.submit({
            while (!stop.get()) {
                action.call()
            }
        } as Runnable)
        try {
            long deadline = System.nanoTime() + LIMIT_NANOS
            for (int i = 0; i < CALLS && events.CREATED.get() <= expectedInterceptors && System.nanoTime() < deadline; i++) {
                proxy.work()
            }
        } finally {
            stop.set(true)
            other.get(30, TimeUnit.SECONDS)
            executor.shutdownNow()
        }
    }

    void 'test a call that races with a swap intercepts the target it reaches with the interceptors of that target'() {
        given:
        ApplicationContext context = buildContext('swapping.MyBean', source('''
@Around(proxyTarget = true, hotswap = true, lazyInterceptorsPerTarget = true)
@Probed
public class MyBean {
    final String name;

    MyBean(String name) { this.name = name; }

    MyBean() { this("proxy"); }

    // public: a factory produced bean is advised on its public methods
    public String work() { return name; }
}

@Factory
class MyBeans {
    @Singleton
    @Named("one")
    MyBean one() { return new MyBean("one"); }

    @Singleton
    @Named("two")
    MyBean two() { return new MyBean("two"); }
}
'''), true)
        def events = context.classLoader.loadClass('swapping.Events')
        def type = context.classLoader.loadClass('swapping.MyBean')
        def proxy = context.getBean(type, Qualifiers.byName('one'))
        def other = context.getBean(type, Qualifiers.byName('two'))
        def one = ((InterceptedProxy) proxy).interceptedTarget()
        def two = ((InterceptedProxy) other).interceptedTarget()

        expect: 'each target, which the context holds a registration for, has its own interceptor'
        proxy.work() == 'one'
        other.work() == 'two'
        events.CREATED.get() == 2

        when: 'the proxy is called while its target is swapped between the two'
        boolean first = false
        callWhile(proxy, events, 2) {
            ((HotSwappableInterceptedProxy) proxy).swap(first ? one : two)
            first = !first
        }

        then: 'no interceptor was created for a target taken as unmanaged, and each target kept its own'
        events.CREATED.get() == 2
        events.SEEN['one'].size() == 1
        events.SEEN['two'].size() == 1
        events.SEEN['one'] != events.SEEN['two']

        cleanup:
        context.close()
    }
}
