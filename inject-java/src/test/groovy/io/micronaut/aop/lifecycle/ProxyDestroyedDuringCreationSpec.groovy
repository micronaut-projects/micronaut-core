package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class ProxyDestroyedDuringCreationSpec extends AbstractTypeElementSpec {

    void "a proxy destroyed by a creation listener does not strand the dependents created with it"() {
        given: 'a proxied prototype with a prototype interceptor, and a listener that destroys the proxy as it is created'
        def ctx = buildContext('''
package test;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.inject.proxy.InterceptedBeanProxy;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

class Log {
    static final List<String> events = new CopyOnWriteArrayList<>();
}

@Retention(RetentionPolicy.RUNTIME)
@Around(proxyTarget = true)
@interface Wrapped {
}

@Prototype
@InterceptorBean(Wrapped.class)
class Advice implements MethodInterceptor<Object, Object> {

    Advice() {
        Log.events.add("advice created");
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed();
    }

    @PreDestroy
    void stop() {
        Log.events.add("advice destroyed");
    }
}

@Prototype
@Wrapped
class Target {

    public String run() {
        return "ok";
    }
}

@Singleton
class Destroyer implements BeanCreatedEventListener<Target> {

    private final BeanContext context;

    Destroyer(BeanContext context) {
        this.context = context;
    }

    @Override
    public Target onCreated(BeanCreatedEvent<Target> event) {
        if (event.getBean() instanceof InterceptedBeanProxy) {
            context.destroyBean(event.getBean());
        }
        return event.getBean();
    }
}
''')
        def type = ctx.classLoader.loadClass('test.Target')
        def log = ctx.classLoader.loadClass('test.Log')

        when: 'the proxy is created, and destroyed by the listener before its registration is complete'
        ctx.getBean(type)

        then: 'the interceptor created with the proxy is destroyed too'
        log.events.count('advice created') > 0
        log.events.count('advice destroyed') == log.events.count('advice created')

        cleanup:
        ctx.close()
    }

    void "destroying a proxy removes its scoped target even when a dependent fails to be destroyed"() {
        given: 'a scoped proxy whose prototype interceptor fails when it is destroyed'
        def ctx = buildContext('''
package test;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.scope.AbstractConcurrentCustomScope;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.runtime.context.scope.ScopedProxy;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

class Log {
    static final List<String> events = new CopyOnWriteArrayList<>();
}

@ScopedProxy
@Scope
@Retention(RetentionPolicy.RUNTIME)
@interface Held {
}

@Singleton
class HeldScope extends AbstractConcurrentCustomScope<Held> {

    final Map<BeanIdentifier, CreatedBean<?>> beans = new ConcurrentHashMap<>();

    HeldScope() {
        super(Held.class);
    }

    @Override
    protected Map<BeanIdentifier, CreatedBean<?>> getScopeMap(boolean forCreation) {
        return beans;
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public void close() {
        destroyScope(beans);
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Around
@interface Wrapped {
}

@Prototype
@InterceptorBean(Wrapped.class)
class Advice implements MethodInterceptor<Object, Object> {

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed();
    }

    @PreDestroy
    void stop() {
        throw new AssertionError("advice failed");
    }
}

@Held
@Wrapped
class Target {

    public String run() {
        return "ok";
    }

    @PreDestroy
    void stop() {
        Log.events.add("target destroyed");
    }
}
''')
        def type = ctx.classLoader.loadClass('test.Target')
        def log = ctx.classLoader.loadClass('test.Log')
        def scope = ctx.getBean(ctx.classLoader.loadClass('test.HeldScope'))
        def proxy = ctx.getBean(type)

        expect: 'the target is created into the scope on first use'
        proxy.run() == 'ok'
        !scope.beans.isEmpty()

        when: 'the proxy is destroyed'
        ctx.destroyBean(proxy)

        then: 'the failure of the interceptor is reported, and the target is still removed from its scope'
        def failure = thrown(AssertionError)
        failure.message == 'advice failed'
        log.events == ['target destroyed']
        scope.beans.isEmpty()

        cleanup:
        ctx.close()
    }
}
