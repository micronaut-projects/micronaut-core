package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.InterceptedProxy
import io.micronaut.context.ApplicationContext

import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit

/**
 * Regressions of the ownership rule found by review: an interceptor created late for a target keeps its own
 * dependencies, a custom scope that cannot find a bean's registration again still hands back the same one,
 * concurrent first calls create one instance, and a runtime proxy creator that reads a method's own interceptors
 * still applies the singletons.
 */
class InterceptorOwnershipRegressionSpec extends AbstractTypeElementSpec {

    void 'test an interceptor created for a target keeps a prototype of its own and hands only itself to the target'() {
        given:
        ApplicationContext context = buildContext('''
package ownership.nested;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around(proxyTarget = true, lazy = true, cacheableLazyTarget = true)
@interface Traced {
}

@Prototype
class Pen {
    static final List<String> events = new ArrayList<>();
    @PreDestroy void destroy() { events.add("PEN_DESTROYED"); }
}

// bound for AROUND alone, so it is created by the proxy for the target, not with the target; it needs a prototype
@Prototype
@InterceptorBinding(value = Traced.class, kind = InterceptorKind.AROUND)
class TracingInterceptor implements MethodInterceptor<Object, Object> {
    static int instances;
    final Pen pen;
    TracingInterceptor(Pen pen) { this.pen = pen; instances++; }
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed();
    }
    @PreDestroy void destroy() { Pen.events.add("TRACER_DESTROYED"); }
}

@Singleton
@Traced
class MyBean {
    public String work() { return "done"; }
}
''')
        def tracerType = context.classLoader.loadClass('ownership.nested.TracingInterceptor')
        def penType = context.classLoader.loadClass('ownership.nested.Pen')
        def bean = context.getBean(context.classLoader.loadClass('ownership.nested.MyBean'))

        when:
        bean.work()
        bean.work()
        def target = ((InterceptedProxy) bean).interceptedTarget()
        def dependents = context.findBeanRegistration(target).get().dependentBeans

        then: 'the target owns the interceptor, and the interceptor alone; the pen belongs to the interceptor'
        tracerType.instances == 1
        dependents*.bean*.getClass() == [tracerType]
        dependents[0].dependentBeans*.bean*.getClass() == [penType]

        when:
        context.stop()

        then: 'both are destroyed with the target, the pen after the interceptor that holds it'
        penType.events == ['TRACER_DESTROYED', 'PEN_DESTROYED']

        cleanup:
        context.close()
    }

    void 'test a custom scope that cannot find a registration again still hands back the one it created'() {
        given:
        ApplicationContext context = buildContext('''
package ownership.scope;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.scope.BeanCreationContext;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.runtime.context.scope.ScopedProxy;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@ScopedProxy
@Scope
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@interface Minimal {
}

// implements only what CustomScope requires: it cannot find a registration by bean or by definition
@Singleton
class MinimalScope implements CustomScope<Minimal> {
    private final Map<BeanIdentifier, CreatedBean<?>> beans = new HashMap<>();
    public Class<Minimal> annotationType() { return Minimal.class; }
    public <T> T getOrCreate(BeanCreationContext<T> creationContext) {
        return (T) beans.computeIfAbsent(creationContext.id(), id -> creationContext.create()).bean();
    }
    public <T> Optional<T> remove(BeanIdentifier identifier) {
        CreatedBean<?> removed = beans.remove(identifier);
        return removed == null ? Optional.empty() : Optional.of((T) removed.bean());
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@interface Counted {
}

@Prototype
@InterceptorBean(Counted.class)
class CountingInterceptor implements MethodInterceptor<Object, Object> {
    static int instances;
    final int id = ++instances;
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return id;
    }
}

@Minimal
@Counted
class MinimalBean {
    public int call() { return 0; }
}
''')
        def interceptorType = context.classLoader.loadClass('ownership.scope.CountingInterceptor')
        def bean = context.getBean(context.classLoader.loadClass('ownership.scope.MinimalBean'))

        when:
        def first = bean.call()
        def second = bean.call()
        def third = bean.call()

        then: 'every call reaches the one instance created for the scoped target'
        [first, second, third] == [1, 1, 1]
        interceptorType.instances == 1

        cleanup:
        context.close()
    }

    void 'test concurrent first calls on a lazy proxy create one interceptor for the target'() {
        given:
        ApplicationContext context = buildContext('''
package ownership.concurrent;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around(proxyTarget = true, lazy = true)
@interface Traced {
}

@Prototype
@InterceptorBinding(value = Traced.class, kind = InterceptorKind.AROUND)
class TracingInterceptor implements MethodInterceptor<Object, Object> {
    static final AtomicInteger instances = new AtomicInteger();
    TracingInterceptor() { instances.incrementAndGet(); }
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed();
    }
}

@Singleton
@Traced
class MyBean {
    public String work() { return "done"; }
}
''')
        def tracerType = context.classLoader.loadClass('ownership.concurrent.TracingInterceptor')
        def bean = context.getBean(context.classLoader.loadClass('ownership.concurrent.MyBean'))
        int threads = 8
        def barrier = new CyclicBarrier(threads)
        def done = new CountDownLatch(threads)
        def failures = Collections.synchronizedList([])

        when:
        (1..threads).each {
            Thread.start {
                try {
                    barrier.await(10, TimeUnit.SECONDS)
                    bean.work()
                } catch (Throwable e) {
                    failures << e
                } finally {
                    done.countDown()
                }
            }
        }
        done.await(30, TimeUnit.SECONDS)

        then: 'the target has exactly one instance of the interceptor, whichever call created it'
        failures.empty
        tracerType.instances.get() == 1

        cleanup:
        context.close()
    }

    void 'test a runtime proxy creator that reads the methods own interceptors still applies the singletons'() {
        given:
        def context = buildContext('''
package ownership.legacycreator;

import io.micronaut.aop.*;
import io.micronaut.aop.runtime.RuntimeProxy;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Around(proxyTarget = true)
@interface Counted {
}

@Singleton
@InterceptorBean(Counted.class)
class CountingInterceptor implements MethodInterceptor<Object, Object> {
    static int calls;
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        calls++;
        return context.proceed();
    }
}

@Prototype
@Counted
@RuntimeProxy(io.micronaut.aop.LegacyByteBuddyRuntimeProxy.class)
class Fronted {
    public String call() { return "called"; }
}
''')
        context.registerSingleton(new io.micronaut.aop.LegacyByteBuddyRuntimeProxy())
        def counting = context.classLoader.loadClass('ownership.legacycreator.CountingInterceptor')

        when:
        def result = context.getBean(context.classLoader.loadClass('ownership.legacycreator.Fronted')).call()

        then: 'a prototype target with singleton-only advice is advised through a creator that never asks per target'
        result == 'called'
        counting.calls == 1

        cleanup:
        context.close()
    }
}
