package io.micronaut.aop.lazyproxytarget

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanResolutionContext
import io.micronaut.context.scope.CustomScope
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.context.scope.ThreadLocal

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
