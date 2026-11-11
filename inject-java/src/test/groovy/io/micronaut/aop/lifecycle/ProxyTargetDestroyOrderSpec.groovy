package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import spock.lang.Unroll

/**
 * A proxy that resolved its interceptors for itself destroys its target first, whether it is destroyed with
 * {@code destroyBean(Object)} or with the bean that injected it: the pre-destroy of the target is intercepted by the
 * interceptor instance the proxy held, which is still alive then and is destroyed, once, after it.
 */
class ProxyTargetDestroyOrderSpec extends AbstractTypeElementSpec {

    private static final List<String> SHAPES = [
        '@Around(proxyTarget = true)',
        '@Around(proxyTarget = true, lazy = true, cacheableLazyTarget = true)',
        '@Around(proxyTarget = true, hotswap = true)'
    ]

    private static String source(String around, String extraBinding = '', String postConstruct = '') {
        """
package destroyorder;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.AROUND)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
$extraBinding
@interface Watched {
}

@Prototype
@InterceptorBinding(value = Watched.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Watched.class, kind = InterceptorKind.PRE_DESTROY)
${extraBinding.replace('(', '(value = Watched.class, ')}
class WatchingInterceptor implements MethodInterceptor<Object, Object> {
    static final List<String> EVENTS = Collections.synchronizedList(new ArrayList<>());
    static int instances;
    final int id = ++instances;
    boolean destroyed;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        EVENTS.add(id + ":" + context.getKind() + (destroyed ? ":AFTER_DESTROYED" : ""));
        return context.proceed();
    }

    @PreDestroy
    void destroy() {
        destroyed = true;
        EVENTS.add(id + ":DESTROYED");
    }
}

@Prototype
$around
@Watched
class WatchedBean {
    $postConstruct

    public String work() {
        return "done";
    }

    @PreDestroy
    void close() {
        WatchingInterceptor.EVENTS.add("target:CLOSED");
    }
}

@Singleton
class Holder {
    @Inject WatchedBean bean;
}
"""
    }

    @Unroll
    void 'test a target destroyed through its proxy is destroyed before the interceptors the proxy held: #around'() {
        given:
        ApplicationContext context = buildContext(source(around))
        def interceptor = context.classLoader.loadClass('destroyorder.WatchingInterceptor')
        def bean = context.getBean(context.classLoader.loadClass('destroyorder.WatchedBean'))

        when:
        bean.work()
        context.destroyBean(bean)
        List<String> events = new ArrayList<>(interceptor.EVENTS)

        then: 'the instance that intercepted the methods intercepted the pre destroy of the target, and was destroyed once, after it'
        events == ['1:AROUND', '1:PRE_DESTROY', 'target:CLOSED', '1:DESTROYED']
        interceptor.instances == 1

        when:
        context.close()

        then: 'nothing is destroyed again'
        new ArrayList<>(interceptor.EVENTS) == events

        where:
        around << SHAPES
    }

    @Unroll
    void 'test a target destroyed with the bean that injected its proxy is destroyed before the interceptors the proxy held: #around'() {
        given:
        ApplicationContext context = buildContext(source(around))
        def interceptor = context.classLoader.loadClass('destroyorder.WatchingInterceptor')
        def holder = context.getBean(context.classLoader.loadClass('destroyorder.Holder'))

        when:
        holder.bean.work()
        context.close()

        then: 'no interceptor is created for the pre destroy of the target, and the one the proxy held is destroyed once, after it'
        new ArrayList<>(interceptor.EVENTS) == ['1:AROUND', '1:PRE_DESTROY', 'target:CLOSED', '1:DESTROYED']
        interceptor.instances == 1

        where:
        around << SHAPES
    }

    @Unroll
    void 'test the interceptor created with a target gives way to the one the proxy held: #around, injected #injected'() {
        given:
        ApplicationContext context = buildContext(source(
            around,
            '@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)',
            '@jakarta.annotation.PostConstruct void init() {}'
        ))
        def interceptor = context.classLoader.loadClass('destroyorder.WatchingInterceptor')

        when:
        if (injected) {
            context.getBean(context.classLoader.loadClass('destroyorder.Holder')).bean.work()
            context.close()
        } else {
            def bean = context.getBean(context.classLoader.loadClass('destroyorder.WatchedBean'))
            bean.work()
            context.destroyBean(bean)
        }
        List<String> events = new ArrayList<>(interceptor.EVENTS)

        then: 'the pre destroy of the target is intercepted once, by the instance the proxy held, before anything is destroyed'
        events.take(4) == ['2:POST_CONSTRUCT', '1:AROUND', '1:PRE_DESTROY', 'target:CLOSED']
        events.count('1:DESTROYED') == 1

        and: 'the target interceptor is destroyed once whether destruction uses the instance or its owner'
        events.drop(4) as Set == ['1:DESTROYED', '2:DESTROYED'] as Set
        events.size() == 6

        cleanup:
        context.close()

        where:
        [around, injected] << [SHAPES, [false, true]].combinations()
    }
}
