package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import java.lang.ref.WeakReference

/**
 * Regressions found by review of the ownership rule: a prototype proxy the caller holds keeps what it owns for as long
 * as it lives, a runtime creator that reads the methods' own interceptors still destroys them with the proxy, and a
 * lazy runtime proxy leaves its target to the first call.
 */
class InterceptorLifetimeRegressionSpec extends AbstractTypeElementSpec {
    void 'a live prototype proxy keeps its interceptor for destruction after GC'() {
        given:
        def context = buildContext('''
package review.retention;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import java.lang.annotation.*;
import java.util.*;
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {}
@Prototype
@InterceptorBean(Tracked.class)
class Tracking implements MethodInterceptor<Object, Object> {
    static int next;
    static final List<Integer> calls = new ArrayList<>();
    static final List<Integer> pres = new ArrayList<>();
    static final List<Integer> closed = new ArrayList<>();
    final int id = ++next;
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        (ctx.getKind() == InterceptorKind.PRE_DESTROY ? pres : calls).add(id);
        return ctx.proceed();
    }
    @PreDestroy void close() { closed.add(id); }
}
@Prototype @Tracked
class TargetBean {
    public String call() { return "ok"; }
    @PreDestroy void close() {}
}
''')
        def type = context.classLoader.loadClass('review.retention.TargetBean')
        def tracking = context.classLoader.loadClass('review.retention.Tracking')
        def bean = context.getBean(type)
        bean.call()
        def registration = new WeakReference(context.findBeanRegistration(bean).get())

        when:
        for (int i = 0; i < 100 && registration.get() != null; i++) {
            System.gc()
            Thread.sleep(20)
        }
        context.destroyBean(bean)

        then:
        tracking.calls == [1]
        tracking.pres == [1]
        tracking.closed == [1]

        cleanup:
        context.close()
    }

    void 'legacy runtime proxy destroys prototype method interceptors with its target'() {
        given:
        def context = buildContext('''
package review.legacy;
import io.micronaut.aop.*;
import io.micronaut.aop.runtime.RuntimeProxy;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around(proxyTarget = true)
@interface Counted {}
@Prototype
@InterceptorBean(Counted.class)
class CountingInterceptor implements MethodInterceptor<Object, Object> {
    static int destroyed;
    public Object intercept(MethodInvocationContext<Object, Object> ctx) { return ctx.proceed(); }
    @PreDestroy void close() { destroyed++; }
}
@Prototype @Counted
@RuntimeProxy(io.micronaut.aop.LegacyByteBuddyRuntimeProxy.class)
class TargetBean {
    public String call() { return "ok"; }
}
''')
        context.registerSingleton(new io.micronaut.aop.LegacyByteBuddyRuntimeProxy())
        def type = context.classLoader.loadClass('review.legacy.TargetBean')
        def tracking = context.classLoader.loadClass('review.legacy.CountingInterceptor')
        def registration = context.getBeanRegistration(type, null)
        registration.bean.call()

        when:
        context.destroyBean(registration)

        then:
        tracking.destroyed == 1

        cleanup:
        context.close()
    }

    void 'runtime proxy creation preserves lazy singleton target resolution'() {
        given:
        def context = buildContext('''
package review.lazy;
import io.micronaut.aop.*;
import io.micronaut.aop.runtime.*;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around(proxyTarget = true, lazy = true)
@interface Counted {}
@Singleton @InterceptorBean(Counted.class)
class CountingInterceptor implements MethodInterceptor<Object, Object> {
    public Object intercept(MethodInvocationContext<Object, Object> ctx) { return ctx.proceed(); }
}
interface Service { String call(); }
@Factory
class Beans {
    static int created;
    @Singleton @Counted @RuntimeProxy(LazyCreator.class)
    Service service() { created++; return () -> "ok"; }
}
@Singleton
class LazyCreator implements RuntimeProxyCreator {
    public boolean selectsInterceptorsPerTarget() { return true; }
    @SuppressWarnings("unchecked")
    public <T> T createProxy(RuntimeProxyDefinition<T> definition) {
        return (T) java.lang.reflect.Proxy.newProxyInstance(Service.class.getClassLoader(), new Class[]{Service.class},
            (proxy, method, args) -> method.invoke(definition.targetBean(), args));
    }
}
''')
        def beans = context.classLoader.loadClass('review.lazy.Beans')
        def bean = context.getBean(context.classLoader.loadClass('review.lazy.Service'))

        expect:
        beans.created == 0

        when:
        bean.call()

        then:
        beans.created == 1

        cleanup:
        context.close()
    }
}
