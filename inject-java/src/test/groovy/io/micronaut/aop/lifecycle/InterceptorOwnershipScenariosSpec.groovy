package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * The scenarios micronaut-jakarta-interceptors and micronaut-cdi run into, written with Micronaut interceptors alone:
 * the proxies whose target the proxy could not find the interceptors of, and the rule of the interceptors
 * specification that the interceptor instances of an object are created with it (CDI TCK
 * {@code InterceptorLifeCycleTest}, assertion {@code ba}) and destroyed with it.
 */
class InterceptorOwnershipScenariosSpec extends AbstractTypeElementSpec {

    private static final String PAIRED = '''
package ownership.scenarios;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around(proxyTarget = true, lazy = LAZY, hotswap = HOTSWAP)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Paired {
}

// one class for every kind, as the advice of micronaut-jakarta-interceptors is
@Prototype
@InterceptorBinding(value = Paired.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Paired.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Paired.class, kind = InterceptorKind.PRE_DESTROY)
class PairedInterceptor implements MethodInterceptor<Object, Object> {
    static final List<PairedInterceptor> POST_CONSTRUCTED = new ArrayList<>();
    static final List<PairedInterceptor> INVOKED = new ArrayList<>();
    static final List<PairedInterceptor> DESTROYED = new ArrayList<>();

    String state;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        switch (context.getKind()) {
            case POST_CONSTRUCT -> {
                state = "initialized";
                POST_CONSTRUCTED.add(this);
            }
            case AROUND -> INVOKED.add(this);
            default -> { }
        }
        return context.proceed();
    }

    @PreDestroy
    void destroy() {
        DESTROYED.add(this);
    }
}

@SCOPE
@Paired
class TargetService {
    public String work() { return "done"; }
}
'''

    void 'test one interceptor instance serves the target and the proxy of a #description'() {
        given:
        ApplicationContext context = buildContext('ownership.scenarios.TargetService', PAIRED
            .replace('LAZY', lazy.toString())
            .replace('HOTSWAP', hotswap.toString())
            .replace('SCOPE', scope), true)
        Class<?> interceptorType = context.classLoader.loadClass('ownership.scenarios.PairedInterceptor')
        Class<?> beanType = context.classLoader.loadClass('ownership.scenarios.TargetService')

        when:
        prepare.call(context, beanType)
        def bean = context.getBean(beanType)
        bean.work()
        bean.work()

        then: 'the instance that saw the post construct of the target intercepts the calls on the proxy'
        interceptorType.POST_CONSTRUCTED.size() == 1
        interceptorType.INVOKED.size() == 2
        interceptorType.INVOKED.every { it.is(interceptorType.POST_CONSTRUCTED[0]) }
        interceptorType.INVOKED[0].state == 'initialized'

        when:
        context.close()

        then: 'and it is destroyed once, with the target'
        interceptorType.DESTROYED.size() == 1
        interceptorType.DESTROYED[0].is(interceptorType.POST_CONSTRUCTED[0])

        where:
        description                        | scope                                                              | lazy  | hotswap | prepare
        'lazy proxy'                       | 'jakarta.inject.Singleton'                                         | true  | false   | { c, t -> }
        'hot swappable proxy'              | 'jakarta.inject.Singleton'                                         | false | true    | { c, t -> }
        'thread local scoped proxy'        | 'io.micronaut.runtime.context.scope.ThreadLocal(lifecycle = true)' | false | false   | { c, t -> }
        'target created before its proxy'  | 'jakarta.inject.Singleton'                                         | false | false   | { c, t -> c.getProxyTargetBean(t, null) }
        'target created on another thread' | 'jakarta.inject.Singleton'                                         | false | false   | { c, t -> Thread.start { c.getProxyTargetBean(t, null) }.join() }
    }

    private static final String KINDS = '''
package ownership.kinds;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Watched {
}

class Events {
    static final List<String> RECORDED = new ArrayList<>();
}

@Prototype
@InterceptorBinding(value = Watched.class, kind = InterceptorKind.AROUND)
class AroundWatcher implements MethodInterceptor<Object, Object> {
    AroundWatcher() { Events.RECORDED.add("around created"); }
    @Override public Object intercept(MethodInvocationContext<Object, Object> context) { return context.proceed(); }
    @PreDestroy void destroy() { Events.RECORDED.add("around destroyed"); }
}

@Prototype
@InterceptorBinding(value = Watched.class, kind = InterceptorKind.POST_CONSTRUCT)
class PostConstructWatcher implements MethodInterceptor<Object, Object> {
    PostConstructWatcher() { Events.RECORDED.add("post construct created"); }
    @Override public Object intercept(MethodInvocationContext<Object, Object> context) { return context.proceed(); }
    @PreDestroy void destroy() { Events.RECORDED.add("post construct destroyed"); }
}

@Prototype
@InterceptorBinding(value = Watched.class, kind = InterceptorKind.PRE_DESTROY)
class PreDestroyWatcher implements MethodInterceptor<Object, Object> {
    PreDestroyWatcher() { Events.RECORDED.add("pre destroy created"); }
    @Override public Object intercept(MethodInvocationContext<Object, Object> context) { return context.proceed(); }
    @PreDestroy void destroy() { Events.RECORDED.add("pre destroy destroyed"); }
}

@Prototype
@Watched
class Warrior {
    public String attack() { return "attacked"; }
}
'''

    void 'test the interceptor instances of a bean are created with it, whichever kind they are bound for'() {
        given:
        ApplicationContext context = buildContext(KINDS)
        def events = context.classLoader.loadClass('ownership.kinds.Events').RECORDED
        def beanType = context.classLoader.loadClass('ownership.kinds.Warrior')

        when:
        def warrior = context.getBean(beanType)

        then: 'no business method has been invoked, and no destruction begun, yet every interceptor exists'
        events.toSorted() == ['around created', 'post construct created', 'pre destroy created']

        when:
        warrior.attack()
        context.destroyBean(warrior)

        then: 'and each is destroyed with the bean'
        events.findAll { it.endsWith('destroyed') }.toSorted() ==
            ['around destroyed', 'post construct destroyed', 'pre destroy destroyed']

        cleanup:
        context.close()
    }

    void 'test an interceptor that cannot be created fails the bean and the interceptors created before it are destroyed'() {
        given:
        ApplicationContext context = buildContext('''
package ownership.failing;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@interface Rolled {
}

class Events {
    static final List<String> RECORDED = new ArrayList<>();
}

@Prototype
@InterceptorBean(Rolled.class)
class FirstInterceptor implements MethodInterceptor<Object, Object> {
    FirstInterceptor() { Events.RECORDED.add("first created"); }
    @Override public int getOrder() { return 1; }
    @Override public Object intercept(MethodInvocationContext<Object, Object> context) { return context.proceed(); }
    @PreDestroy void destroy() { Events.RECORDED.add("first destroyed"); }
}

@Prototype
@InterceptorBean(Rolled.class)
class SecondInterceptor implements MethodInterceptor<Object, Object> {
    SecondInterceptor() { throw new IllegalStateException("this interceptor cannot be created"); }
    @Override public int getOrder() { return 2; }
    @Override public Object intercept(MethodInvocationContext<Object, Object> context) { return context.proceed(); }
}

@Prototype
@Rolled
class RolledService {
    public String work() { return "done"; }
}
''')
        def events = context.classLoader.loadClass('ownership.failing.Events').RECORDED

        when:
        context.getBean(context.classLoader.loadClass('ownership.failing.RolledService'))

        then: 'the bean is not created'
        thrown(RuntimeException)

        and: 'nor is anything it owned left behind'
        events == ['first created', 'first destroyed']

        cleanup:
        context.close()
    }

    void 'test the interceptors of a bean are destroyed when a pre destroy interceptor does not proceed'() {
        given:
        ApplicationContext context = buildContext('''
package ownership.suppressed;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Suppressed {
}

class Events {
    static final List<String> RECORDED = new ArrayList<>();
}

@Prototype
@InterceptorBinding(value = Suppressed.class, kind = InterceptorKind.PRE_DESTROY)
class SuppressingInterceptor implements MethodInterceptor<Object, Object> {
    @Override public int getOrder() { return 1; }
    @Override public Object intercept(MethodInvocationContext<Object, Object> context) {
        Events.RECORDED.add("pre destroy suppressed");
        return context.getTarget();
    }
}

@Prototype
@InterceptorBinding(value = Suppressed.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Suppressed.class, kind = InterceptorKind.PRE_DESTROY)
class OwnedInterceptor implements MethodInterceptor<Object, Object> {
    OwnedInterceptor() { Events.RECORDED.add("owned created"); }
    @Override public int getOrder() { return 2; }
    @Override public Object intercept(MethodInvocationContext<Object, Object> context) { return context.proceed(); }
    @PreDestroy void destroy() { Events.RECORDED.add("owned destroyed"); }
}

@Prototype
@Suppressed
class SuppressedService {
    public String work() { return "done"; }
}
''')
        def events = context.classLoader.loadClass('ownership.suppressed.Events').RECORDED
        def bean = context.getBean(context.classLoader.loadClass('ownership.suppressed.SuppressedService'))
        bean.work()

        expect:
        events == ['owned created']

        when:
        events.clear()
        context.destroyBean(bean)

        then: 'the chain stopped, but the bean is destroyed and so is what it owned'
        events == ['pre destroy suppressed', 'owned destroyed']

        cleanup:
        context.close()
    }
}
