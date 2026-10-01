package io.micronaut.aop.proxy

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext

/**
 * Every generated proxy retains the interceptor registrations its constructor was given, not only a proxy whose
 * bean has intercepted lifecycle callbacks.
 */
class InterceptorRegistrationRetentionSpec extends AbstractTypeElementSpec {

    private static List<String> interceptorNames(Object bean) {
        assert bean instanceof Intercepted
        ((Intercepted) bean).$interceptorRegistrations()*.bean*.getClass()*.simpleName
    }

    void 'test an around only proxy retains the around interceptors bound to it'() {
        given:
        ApplicationContext context = buildContext('''
package retention.around;

import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@interface Aro {
}

@Singleton
@InterceptorBinding(value = Aro.class, kind = InterceptorKind.AROUND)
class AroundInterceptor implements Interceptor<Object, Object> {
    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        return context.proceed();
    }
}

@Singleton
@Aro
class MyBean {
    String work() { return "done"; }
}
''')

        when:
        def bean = context.getBean(context.classLoader.loadClass('retention.around.MyBean'))

        then: 'the proxy reports the interceptor it was built with'
        bean.work() == 'done'
        interceptorNames(bean) == ['AroundInterceptor']

        cleanup:
        context.close()
    }

    void 'test an introduction only proxy retains the introduction interceptors bound to it'() {
        given:
        ApplicationContext context = buildContext('''
package retention.introduction;

import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Introduction
@interface Stub {
}

@Singleton
@InterceptorBinding(value = Stub.class, kind = InterceptorKind.INTRODUCTION)
class StubInterceptor implements Interceptor<Object, Object> {
    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        return "stubbed";
    }
}

@Stub
interface MyItfce {
    String work();
}
''')

        when:
        def bean = context.getBean(context.classLoader.loadClass('retention.introduction.MyItfce'))

        then:
        bean.work() == 'stubbed'
        interceptorNames(bean) == ['StubInterceptor']

        cleanup:
        context.close()
    }

    void 'test a proxy with lifecycle advice still retains the superset covering the lifecycle bindings'() {
        given:
        ApplicationContext context = buildContext('''
package retention.lifecycle;

import io.micronaut.aop.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@interface Aro {
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Life {
}

@Singleton
@InterceptorBinding(value = Aro.class, kind = InterceptorKind.AROUND)
class AroundInterceptor implements Interceptor<Object, Object> {
    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        return context.proceed();
    }
}

@Singleton
@InterceptorBinding(value = Life.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Life.class, kind = InterceptorKind.PRE_DESTROY)
class LifecycleInterceptor implements Interceptor<Object, Object> {
    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        return context.proceed();
    }
}

@Singleton
@Aro
@Life
class MyBean {
    @PostConstruct void init() {}
    String work() { return "done"; }
    @PreDestroy void close() {}
}
''')

        when:
        def bean = context.getBean(context.classLoader.loadClass('retention.lifecycle.MyBean'))

        then: 'the constructor qualifier is still widened, so the list covers the lifecycle binding too'
        interceptorNames(bean).toSorted() == ['AroundInterceptor', 'LifecycleInterceptor']

        cleanup:
        context.close()
    }

    void 'test a proxy with both around and lifecycle advice under one annotation reports one list'() {
        given:
        ApplicationContext context = buildContext('''
package retention.both;

import io.micronaut.aop.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.PRE_DESTROY)
class TrackingInterceptor implements Interceptor<Object, Object> {
    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        return context.proceed();
    }
}

@Singleton
@Tracked
class MyBean {
    @PostConstruct void init() {}
    String work() { return "done"; }
    @PreDestroy void close() {}
}
''')

        when:
        def bean = context.getBean(context.classLoader.loadClass('retention.both.MyBean'))

        then:
        bean.work() == 'done'
        interceptorNames(bean) == ['TrackingInterceptor']

        cleanup:
        context.close()
    }

    void 'test two beans advised by a prototype interceptor report different interceptor instances'() {
        given:
        ApplicationContext context = buildContext('''
package retention.prototype;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@interface Aro {
}

@Prototype
@InterceptorBinding(value = Aro.class, kind = InterceptorKind.AROUND)
class AroundInterceptor implements Interceptor<Object, Object> {
    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        return context.proceed();
    }
}

@Prototype
@Aro
class MyBean {
    String work() { return "done"; }
}
''')

        when:
        Class<?> beanType = context.classLoader.loadClass('retention.prototype.MyBean')
        def first = context.getBean(beanType)
        def second = context.getBean(beanType)

        then:
        !first.is(second)
        interceptorNames(first) == ['AroundInterceptor']
        interceptorNames(second) == ['AroundInterceptor']

        and: 'each proxy holds its own interceptor instance'
        !((Intercepted) first).$interceptorRegistrations()[0].bean
                .is(((Intercepted) second).$interceptorRegistrations()[0].bean)

        cleanup:
        context.close()
    }

    void 'test a bean with around and lifecycle advice bound by different annotations still selects the right interceptors'() {
        given:
        ApplicationContext context = buildContext('''
package retention.mixed;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@interface Aro {
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Life {
}

@Prototype
@InterceptorBinding(value = Aro.class, kind = InterceptorKind.AROUND)
class AroundInterceptor implements Interceptor<Object, Object> {
    static final List<String> events = new ArrayList<>();
    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        events.add(((MethodInvocationContext<?, ?>) context).getKind().name());
        return context.proceed();
    }
}

@Prototype
@InterceptorBinding(value = Life.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Life.class, kind = InterceptorKind.PRE_DESTROY)
class LifecycleInterceptor implements Interceptor<Object, Object> {
    static final List<String> events = new ArrayList<>();
    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        events.add(((MethodInvocationContext<?, ?>) context).getKind().name());
        return context.proceed();
    }
}

@Singleton
@Aro
@Life
class MyBean {
    @PostConstruct void init() {}
    String work() { return "done"; }
    @PreDestroy void close() {}
}
''')
        Class<?> around = context.classLoader.loadClass('retention.mixed.AroundInterceptor')
        Class<?> lifecycle = context.classLoader.loadClass('retention.mixed.LifecycleInterceptor')

        when:
        def bean = context.getBean(context.classLoader.loadClass('retention.mixed.MyBean'))
        bean.work()

        then: 'the around interceptor sees only the method call and the lifecycle one only post construct'
        around.events == ['AROUND']
        lifecycle.events == ['POST_CONSTRUCT']

        when:
        context.stop()

        then: 'and pre destroy still goes to the lifecycle interceptor alone'
        around.events == ['AROUND']
        lifecycle.events == ['POST_CONSTRUCT', 'PRE_DESTROY']

        cleanup:
        context.close()
    }

    // MethodInterceptorChain#doIntercept selects intercepted.$interceptorRegistrations() whenever the list is
    // non-empty. Retaining a list on every proxy makes that branch reachable for an around-only proxy, so pin down
    // that such a proxy still has no lifecycle interception at all: the definition only generates the post-construct
    // and pre-destroy entry points when the bean carries lifecycle bindings, which is the same condition that widens
    // the constructor qualifier.
    void 'test an around only proxy with lifecycle callbacks does not gain lifecycle interception'() {
        given:
        ApplicationContext context = buildContext('''
package retention.aroundlifecycle;

import io.micronaut.aop.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@interface Aro {
}

@Singleton
@InterceptorBinding(value = Aro.class, kind = InterceptorKind.AROUND)
class AroundInterceptor implements Interceptor<Object, Object> {
    static final List<String> events = new ArrayList<>();
    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        events.add(((MethodInvocationContext<?, ?>) context).getKind().name());
        return context.proceed();
    }
}

@Singleton
@Aro
class MyBean {
    static final List<String> callbacks = new ArrayList<>();
    @PostConstruct void init() { callbacks.add("init"); }
    String work() { return "done"; }
    @PreDestroy void close() { callbacks.add("close"); }
}
''')
        Class<?> around = context.classLoader.loadClass('retention.aroundlifecycle.AroundInterceptor')
        Class<?> beanType = context.classLoader.loadClass('retention.aroundlifecycle.MyBean')

        when:
        def bean = context.getBean(beanType)
        bean.work()

        then: 'the retained list is non-empty, but post construct was not routed through the chain'
        interceptorNames(bean) == ['AroundInterceptor']
        beanType.callbacks == ['init']
        around.events == ['AROUND']

        when:
        context.stop()

        then: 'and neither was pre destroy'
        beanType.callbacks == ['init', 'close']
        around.events == ['AROUND']

        cleanup:
        context.close()
    }

    void 'test a proxy target proxy retains the around interceptors bound to it'() {
        given:
        ApplicationContext context = buildContext('''
package retention.proxytarget;

import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around(proxyTarget = true)
@interface Aro {
}

@Singleton
@InterceptorBinding(value = Aro.class, kind = InterceptorKind.AROUND)
class AroundInterceptor implements Interceptor<Object, Object> {
    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        return context.proceed();
    }
}

@Singleton
@Aro
class MyBean {
    String work() { return "done"; }
}
''')

        when:
        def bean = context.getBean(context.classLoader.loadClass('retention.proxytarget.MyBean'))

        then:
        bean.work() == 'done'
        interceptorNames(bean) == ['AroundInterceptor']

        cleanup:
        context.close()
    }
}
