package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * A method returning nothing, on each shape of proxy that fronts a separate target, intercepted by a non-singleton
 * interceptor, which the proxy selects for the target of the call.
 */
class ProxyTargetVoidMethodSpec extends AbstractTypeElementSpec {

    void 'test a void method of a #description is intercepted by the interceptor of its target'() {
        given:
        ApplicationContext context = buildContext("""
package voidmethod.${pkg};

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.scope.AbstractConcurrentCustomScope;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.inject.BeanIdentifier;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
${around}
@interface Pinged {
}

@jakarta.inject.Scope
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@io.micronaut.runtime.context.scope.ScopedProxy
@interface Conversation {
}

@Singleton
class ConversationScope extends AbstractConcurrentCustomScope<Conversation> {
    private final Map<BeanIdentifier, CreatedBean<?>> beans = new ConcurrentHashMap<>();

    ConversationScope() {
        super(Conversation.class);
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

@Prototype
@InterceptorBean(Pinged.class)
class PingInterceptor implements MethodInterceptor<Object, Object> {
    static final List<Object> INVOKED = new ArrayList<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        INVOKED.add(this);
        return context.proceed();
    }
}

@${scope}
@Pinged
class Watcher {
    static int pings;

    public void ping() {
        pings++;
    }
}
""")
        def interceptor = context.classLoader.loadClass("voidmethod.${pkg}.PingInterceptor")
        def watcherType = context.classLoader.loadClass("voidmethod.${pkg}.Watcher")
        def watcher = context.getBean(watcherType)

        when:
        watcher.ping()
        watcher.ping()
        List<Object> invoked = new ArrayList<>(interceptor.INVOKED)

        then: 'each call ran the target once, through one interceptor instance'
        watcherType.pings == 2
        invoked.size() == 2
        invoked[0].is(invoked[1])

        cleanup:
        context.close()

        where:
        description           | pkg         | scope          | around
        'fixed proxy target'  | 'fixed'     | 'Singleton'    | '@Around(proxyTarget = true, lazyInterceptorsPerTarget = true)'
        'lazy proxy target'   | 'lazy'      | 'Singleton'    | '@Around(proxyTarget = true, lazy = true, lazyInterceptorsPerTarget = true)'
        'cached lazy target'  | 'cached'    | 'Singleton'    | '@Around(proxyTarget = true, lazy = true, cacheableLazyTarget = true, lazyInterceptorsPerTarget = true)'
        'hot swappable proxy' | 'hotswap'   | 'Singleton'    | '@Around(proxyTarget = true, hotswap = true, lazyInterceptorsPerTarget = true)'
        'scoped proxy'        | 'scoped'    | 'Conversation' | '@Around(lazyInterceptorsPerTarget = true)'
    }
}
