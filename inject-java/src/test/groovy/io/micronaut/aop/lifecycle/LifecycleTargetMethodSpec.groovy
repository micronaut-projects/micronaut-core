package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * A lifecycle event describes itself by the callback the bean declares, so an interceptor asking
 * {@code getExecutableMethod().getTargetMethod()} is answered with the most derived callback of that kind.
 *
 * A bean may bind a lifecycle kind without declaring a callback of it: the binding on the class promises an
 * interception regardless. Such an event has no target method and reports {@code null}, rather than looking up the
 * synthetic {@code initialize} / {@code dispose} the chain names itself by, which no bean declares. Only the
 * reflective {@link java.lang.reflect.Method} becomes {@code null}; {@code getMethodName()} still reports the
 * synthetic name of the event.
 */
class LifecycleTargetMethodSpec extends AbstractTypeElementSpec {

    void 'test a post construct event of a bean declaring no callback has no target method'() {
        given:
        ApplicationContext context = buildContext('''
package targetmethod.postconstruct;

import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.lang.reflect.Method;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static int intercepted;
    static String methodName;
    static Method target;
    static Method contextTarget;
    static Throwable failure;
    static Object proceeded;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        intercepted++;
        methodName = ctx.getExecutableMethod().getMethodName();
        try {
            target = ctx.getExecutableMethod().getTargetMethod();
            contextTarget = ctx.getTargetMethod();
        } catch (Throwable e) {
            failure = e;
        }
        proceeded = ctx.proceed();
        return proceeded;
    }
}

@Singleton
@Tracked
class NoCallbackBean {
    String work() {
        return "done";
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('targetmethod.postconstruct.TrackingInterceptor')
        Class<?> beanType = context.classLoader.loadClass('targetmethod.postconstruct.NoCallbackBean')

        when:
        def bean = context.getBean(beanType)

        then: 'the event was intercepted and has no target method'
        interceptorType.intercepted == 1
        interceptorType.failure == null
        interceptorType.target == null

        and: 'the context itself answers the same, since it delegates to the executable method'
        interceptorType.contextTarget == null

        and: 'proceed() is a no-op that returns the bean'
        interceptorType.proceeded.is(bean)

        and: 'only the reflective Method is null: the event still names itself'
        interceptorType.methodName == 'initialize'

        cleanup:
        context.close()
    }

    void 'test a pre destroy event of a bean declaring no callback has no target method'() {
        given:
        ApplicationContext context = buildContext('''
package targetmethod.predestroy;

import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.lang.reflect.Method;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.PRE_DESTROY)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static int intercepted;
    static String methodName;
    static Method target;
    static Method contextTarget;
    static Throwable failure;
    static Object proceeded;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        intercepted++;
        methodName = ctx.getExecutableMethod().getMethodName();
        try {
            target = ctx.getExecutableMethod().getTargetMethod();
            contextTarget = ctx.getTargetMethod();
        } catch (Throwable e) {
            failure = e;
        }
        proceeded = ctx.proceed();
        return proceeded;
    }
}

@Singleton
@Tracked
class NoCallbackBean {
    String work() {
        return "done";
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('targetmethod.predestroy.TrackingInterceptor')
        Class<?> beanType = context.classLoader.loadClass('targetmethod.predestroy.NoCallbackBean')

        when:
        def bean = context.getBean(beanType)

        then: 'nothing is intercepted before the bean is destroyed'
        interceptorType.intercepted == 0

        when:
        context.stop()

        then: 'the event was intercepted and has no target method'
        interceptorType.intercepted == 1
        interceptorType.failure == null
        interceptorType.target == null

        and: 'the context itself answers the same, since it delegates to the executable method'
        interceptorType.contextTarget == null

        and: 'proceed() is a no-op that returns the bean'
        interceptorType.proceeded.is(bean)

        and: 'only the reflective Method is null: the event still names itself'
        interceptorType.methodName == 'dispose'

        cleanup:
        context.close()
    }

    void 'test a bound post construct event has no target method where the bean only declares a pre destroy callback'() {
        given:
        ApplicationContext context = buildContext('''
package targetmethod.otherkind.post;

import io.micronaut.aop.*;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.lang.reflect.Method;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static int intercepted;
    static String methodName;
    static Method target;
    static Throwable failure;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        intercepted++;
        methodName = ctx.getExecutableMethod().getMethodName();
        try {
            target = ctx.getExecutableMethod().getTargetMethod();
        } catch (Throwable e) {
            failure = e;
        }
        return ctx.proceed();
    }
}

@Singleton
@Tracked
class MyBean {
    int destroys;

    @PreDestroy
    void close() {
        destroys++;
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('targetmethod.otherkind.post.TrackingInterceptor')
        Class<?> beanType = context.classLoader.loadClass('targetmethod.otherkind.post.MyBean')

        when:
        def bean = context.getBean(beanType)

        then: 'the bound kind is intercepted and the callback of the other kind is not its target'
        interceptorType.intercepted == 1
        interceptorType.failure == null
        interceptorType.target == null
        interceptorType.methodName == 'initialize'

        when: 'the unbound kind runs uninterceptedly'
        context.stop()

        then:
        bean.destroys == 1
        interceptorType.intercepted == 1

        cleanup:
        context.close()
    }

    void 'test a bound pre destroy event has no target method where the bean only declares a post construct callback'() {
        given:
        ApplicationContext context = buildContext('''
package targetmethod.otherkind.pre;

import io.micronaut.aop.*;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.lang.reflect.Method;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.PRE_DESTROY)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static int intercepted;
    static String methodName;
    static Method target;
    static Throwable failure;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        intercepted++;
        methodName = ctx.getExecutableMethod().getMethodName();
        try {
            target = ctx.getExecutableMethod().getTargetMethod();
        } catch (Throwable e) {
            failure = e;
        }
        return ctx.proceed();
    }
}

@Singleton
@Tracked
class MyBean {
    int inits;

    @PostConstruct
    void init() {
        inits++;
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('targetmethod.otherkind.pre.TrackingInterceptor')
        Class<?> beanType = context.classLoader.loadClass('targetmethod.otherkind.pre.MyBean')

        when: 'the unbound kind runs uninterceptedly'
        def bean = context.getBean(beanType)

        then:
        bean.inits == 1
        interceptorType.intercepted == 0

        when:
        context.stop()

        then: 'the bound kind is intercepted and the callback of the other kind is not its target'
        interceptorType.intercepted == 1
        interceptorType.failure == null
        interceptorType.target == null
        interceptorType.methodName == 'dispose'

        cleanup:
        context.close()
    }

    void 'test the target method of an event is the callback the bean declares'() {
        given:
        ApplicationContext context = buildContext('''
package targetmethod.declared;

import io.micronaut.aop.*;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.lang.reflect.Method;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static String methodName;
    static Method target;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        methodName = ctx.getExecutableMethod().getMethodName();
        target = ctx.getExecutableMethod().getTargetMethod();
        return ctx.proceed();
    }
}

@Singleton
@Tracked
class MyBean {
    int inits;

    @PostConstruct
    void init() {
        inits++;
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('targetmethod.declared.TrackingInterceptor')
        Class<?> beanType = context.classLoader.loadClass('targetmethod.declared.MyBean')

        when:
        def bean = context.getBean(beanType)

        then:
        bean.inits == 1
        interceptorType.methodName == 'init'
        interceptorType.target == beanType.getDeclaredMethod('init')

        cleanup:
        context.close()
    }

    void 'test the target method of an event is the most derived callback where a superclass declares one too'() {
        given:
        ApplicationContext context = buildContext('''
package targetmethod.hierarchy;

import io.micronaut.aop.*;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.lang.reflect.Method;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static String methodName;
    static Method target;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        methodName = ctx.getExecutableMethod().getMethodName();
        target = ctx.getExecutableMethod().getTargetMethod();
        return ctx.proceed();
    }
}

class Base {
    int baseInits;

    @PostConstruct
    void baseInit() {
        baseInits++;
    }
}

@Singleton
@Tracked
class Sub extends Base {
    int subInits;

    @PostConstruct
    void subInit() {
        subInits++;
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('targetmethod.hierarchy.TrackingInterceptor')
        Class<?> subType = context.classLoader.loadClass('targetmethod.hierarchy.Sub')

        when:
        def bean = context.getBean(subType)

        then: 'both callbacks ran under the one event'
        bean.baseInits == 1
        bean.subInits == 1

        and: 'the event describes itself by the most derived callback'
        interceptorType.methodName == 'subInit'
        interceptorType.target == subType.getDeclaredMethod('subInit')

        cleanup:
        context.close()
    }
}
