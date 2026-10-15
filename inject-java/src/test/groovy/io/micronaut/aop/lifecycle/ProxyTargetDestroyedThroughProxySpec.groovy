package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * A prototype proxy target destroyed through its proxy with {@code destroyBean(Object)}: the proxy resolved its
 * interceptors for itself, and the pre-destroy interception of the target runs with the instance the proxy held.
 */
class ProxyTargetDestroyedThroughProxySpec extends AbstractTypeElementSpec {

    void 'test the pre destroy interception of a prototype target destroyed through its proxy uses the instance of the proxy'() {
        given:
        ApplicationContext context = buildContext('''
package destroyedthroughproxy;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.AROUND)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Watched {
}

@Prototype
@InterceptorBinding(value = Watched.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Watched.class, kind = InterceptorKind.PRE_DESTROY)
class WatchingInterceptor implements MethodInterceptor<Object, Object> {
    static final List<String> EVENTS = new ArrayList<>();
    static int instances;
    final int id = ++instances;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        EVENTS.add(id + ":" + context.getKind());
        return context.proceed();
    }
}

@Prototype
@Around(proxyTarget = true)
@Watched
class WatchedBean {
    public String work() {
        return "done";
    }

    @PreDestroy
    void close() {
        WatchingInterceptor.EVENTS.add("target:CLOSED");
    }
}
''')
        def interceptor = context.classLoader.loadClass('destroyedthroughproxy.WatchingInterceptor')
        def bean = context.getBean(context.classLoader.loadClass('destroyedthroughproxy.WatchedBean'))

        when:
        bean.work()
        context.destroyBean(bean)
        List<String> events = new ArrayList<>(interceptor.EVENTS)
        String proxyInstance = events.find { it.endsWith(':AROUND') }.split(':')[0]

        then: 'the instance that intercepted the methods intercepted the pre destroy of the target'
        events.contains("${proxyInstance}:PRE_DESTROY".toString())
        events.count { it.endsWith(':PRE_DESTROY') } == 1
        events.contains('target:CLOSED')

        cleanup:
        context.close()
    }
}
