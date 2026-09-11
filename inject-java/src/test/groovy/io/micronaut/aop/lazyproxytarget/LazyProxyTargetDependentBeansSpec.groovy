package io.micronaut.aop.lazyproxytarget

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanResolutionContext
import io.micronaut.context.scope.CustomScope
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.context.scope.ThreadLocal

import java.util.concurrent.CyclicBarrier

/**
 * A lazy proxy retains a copy of the resolution context it was created with and resolves its target through that
 * copy on every intercepted call. A target that is created on each call, a prototype or a bean whose scope is not
 * registered, must not be recorded as a dependent of the retained context, which lives as long as the proxy and
 * would otherwise grow with every call.
 */
class LazyProxyTargetDependentBeansSpec extends AbstractTypeElementSpec {

    private static final int CALLS = 100

    void 'test a prototype lazy proxy target is not retained by the proxy resolution context'() {
        given:
        def context = buildContext(source('@io.micronaut.context.annotation.Prototype'))
        def proxy = lookupProxy(context)

        when:
        def instances = (1..CALLS).collect { proxy.instance() } as Set

        then: 'every call resolves a new target'
        instances.size() == CALLS

        and: 'none of them is retained by the context the proxy keeps'
        retainedResolutionContext(proxy).dependentBeans.isEmpty()

        cleanup:
        context.close()
    }

    void 'test a lazy proxy target of an unregistered scope is not retained by the proxy resolution context'() {
        given: 'a context that does not include the thread local scope bean'
        def context = buildContext(source('@io.micronaut.runtime.context.scope.ThreadLocal'))

        expect: 'the scope is not found, so the target is created as if it had no scope'
        !threadLocalScope(context).isPresent()

        when:
        def proxy = lookupProxy(context)
        def instances = (1..CALLS).collect { proxy.instance() } as Set

        then:
        instances.size() == CALLS
        retainedResolutionContext(proxy).dependentBeans.isEmpty()

        cleanup:
        context.close()
    }

    void 'test a lazy proxy target of a registered scope resolves the scoped instance'() {
        given: 'a context that includes the thread local scope bean'
        def context = buildContext('lazyproxydependents.Test', source('@io.micronaut.runtime.context.scope.ThreadLocal'), true)

        expect:
        threadLocalScope(context).isPresent()

        when:
        def proxy = lookupProxy(context)
        def instances = (1..CALLS).collect { proxy.instance() } as Set

        then:
        instances.size() == 1
        retainedResolutionContext(proxy).dependentBeans.isEmpty()

        cleanup:
        context.close()
    }

    void 'test a cached lazy proxy target is still destroyed with the proxy'() {
        given:
        def context = buildContext('''
package lazyproxydependents;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import java.util.concurrent.atomic.AtomicInteger;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Around(proxyTarget = true, lazy = true, cacheableLazyTarget = true)
@Retention(RUNTIME)
@interface LazilyProxied {
}

@Singleton
@InterceptorBean(LazilyProxied.class)
class LazilyProxiedInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed();
    }
}

@Prototype
class Dependency {
}

@LazilyProxied
@Prototype
class Test {
    static final AtomicInteger DESTROYED = new AtomicInteger();

    final Dependency dependency;

    Test(Dependency dependency) {
        this.dependency = dependency;
    }

    public Object instance() {
        return this;
    }

    public Dependency dependency() {
        return dependency;
    }

    @PreDestroy
    void destroy() {
        DESTROYED.incrementAndGet();
    }
}
''')
        def targetClass = context.classLoader.loadClass('lazyproxydependents.Test')
        def registration = context.getBeanRegistration(context.getBeanDefinition(targetClass))
        def proxy = registration.bean

        when:
        def instances = (1..CALLS).collect { proxy.instance() } as Set

        then: 'the target is created once, with its dependency injected'
        instances.size() == 1
        proxy.dependency() != null

        when:
        context.destroyBean(registration)

        then: 'destroying the proxy destroys the cached target'
        targetClass.DESTROYED.get() == 1

        cleanup:
        context.close()
    }

    void 'test concurrent lazy proxy target creations do not share the lifecycle interceptors of one another'() {
        given: 'a lazy proxy injected while a bean with constructor and post construct advice is being created'
        def context = buildContext('''
package lazyproxydependents;

import io.micronaut.aop.Around;
import io.micronaut.aop.AroundConstruct;
import io.micronaut.aop.ConstructorInvocationContext;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InvocationContext;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Around(proxyTarget = true, lazy = true)
@Retention(RUNTIME)
@interface LazilyProxied {
}

@Singleton
@InterceptorBean(LazilyProxied.class)
class LazilyProxiedInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed();
    }
}

@Retention(RUNTIME)
@AroundConstruct
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Managed {
}

@Prototype
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.POST_CONSTRUCT)
class ManagedInterceptor implements Interceptor<Object, Object> {
    public static volatile CyclicBarrier barrier;
    public static final AtomicInteger postConstructs = new AtomicInteger();
    public static final AtomicInteger mismatches = new AtomicInteger();
    private static final ThreadLocal<Object> CONSTRUCTED_BY = new ThreadLocal<>();

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        CyclicBarrier currentBarrier = barrier;
        if (context instanceof ConstructorInvocationContext<?> constructorContext) {
            if (currentBarrier != null && constructorContext.getConstructor().getDeclaringBeanType() == Target.class) {
                CONSTRUCTED_BY.set(this);
                try {
                    // both threads are constructing a target before either runs its post construct interception
                    currentBarrier.await(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            return context.proceed();
        }
        MethodInvocationContext<?, ?> methodContext = (MethodInvocationContext<?, ?>) context;
        if (currentBarrier != null && methodContext.getTarget() instanceof Target) {
            postConstructs.incrementAndGet();
            if (CONSTRUCTED_BY.get() != this) {
                mismatches.incrementAndGet();
            }
        }
        return context.proceed();
    }
}

@LazilyProxied
@Managed
@Prototype
class Target {
    @PostConstruct
    void init() {
    }

    public Object instance() {
        return this;
    }
}

@Managed
@Singleton
class Holder {
    @Inject
    Target target;

    @PostConstruct
    void init() {
    }
}
''')
        def interceptorClass = context.classLoader.loadClass('lazyproxydependents.ManagedInterceptor')
        def holder = context.getBean(context.classLoader.loadClass('lazyproxydependents.Holder'))
        def proxy = holder.target
        int rounds = 20
        interceptorClass.barrier = new CyclicBarrier(2)

        when: 'two threads create a target through the same proxy at the same time'
        def failures = Collections.synchronizedList([])
        rounds.times {
            def threads = (1..2).collect {
                Thread.start {
                    try {
                        proxy.instance()
                    } catch (Throwable e) {
                        failures << e
                    }
                }
            }
            threads*.join()
        }

        then: 'each target is post constructed by the interceptor that constructed it'
        failures.isEmpty()
        interceptorClass.postConstructs.get() == rounds * 2
        interceptorClass.mismatches.get() == 0

        cleanup:
        interceptorClass?.barrier = null
        context.close()
    }

    private static Optional<CustomScope> threadLocalScope(ApplicationContext context) {
        // how the custom scope registry looks a scope up: a bean, which a test context only has if it includes all beans
        return context.findBean(CustomScope, Qualifiers.byTypeArguments(ThreadLocal))
    }

    private static Object lookupProxy(ApplicationContext context) {
        def proxy = context.getBean(context.classLoader.loadClass('lazyproxydependents.Test'))
        assert proxy.class.name != 'lazyproxydependents.Test'
        return proxy
    }

    private static BeanResolutionContext retainedResolutionContext(Object proxy) {
        def field = proxy.class.getDeclaredField('$beanResolutionContext')
        field.accessible = true
        return (BeanResolutionContext) field.get(proxy)
    }

    private static String source(String scope) {
        return """
package lazyproxydependents;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Around(proxyTarget = true, lazy = true)
@Retention(RUNTIME)
@interface LazilyProxied {
}

@Singleton
@InterceptorBean(LazilyProxied.class)
class LazilyProxiedInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed();
    }
}

@LazilyProxied
$scope
class Test {
    public Object instance() {
        return this;
    }
}
"""
    }
}
