package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class ProxyDestroyedDuringCreationSpec extends AbstractTypeElementSpec {

    void "a proxy destroyed by a creation listener does not strand the dependents created with it"() {
        given:
        def ctx = buildContext('''
package test;
import io.micronaut.aop.*;
import io.micronaut.context.*;
import io.micronaut.context.annotation.*;
import io.micronaut.context.event.*;
import io.micronaut.inject.proxy.InterceptedBeanProxy;
import jakarta.inject.*;
import jakarta.annotation.*;
import java.util.*;
import java.util.concurrent.*;
class Log { static final List<String> events = new CopyOnWriteArrayList<>(); }
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Around(proxyTarget = true)
@interface Wrapped {}
@Prototype @InterceptorBean(Wrapped.class)
class Advice implements MethodInterceptor<Object, Object> {
    Advice() { Log.events.add("advice created"); }
    public Object intercept(MethodInvocationContext<Object, Object> context) { return context.proceed(); }
    @PreDestroy void stop() { Log.events.add("advice destroyed"); }
}
@Prototype @Wrapped class Target {
    public String run() { return "ok"; }
}
@Singleton class Destroyer implements BeanCreatedEventListener<Target> {
    final BeanContext context;
    Destroyer(BeanContext context) { this.context = context; }
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

        when:
        ctx.getBean(type)

        then:
        log.events.count('advice created') > 0
        log.events.count('advice destroyed') == log.events.count('advice created')

        cleanup:
        ctx.close()
    }

    void "destroying a proxy removes its scoped target even when a dependent fails to be destroyed"() {
        given:
        def ctx = buildContext('''
package test;
import io.micronaut.aop.*;
import io.micronaut.context.*;
import io.micronaut.context.annotation.*;
import io.micronaut.context.scope.*;
import io.micronaut.inject.BeanIdentifier;
import jakarta.inject.*;
import jakarta.annotation.*;
import java.util.*;
import java.util.concurrent.*;
class Log { static final List<String> events = new CopyOnWriteArrayList<>(); }
@io.micronaut.runtime.context.scope.ScopedProxy @Scope @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@interface Held {}
@Singleton class HeldScope extends AbstractConcurrentCustomScope<Held> {
    final Map<BeanIdentifier, CreatedBean<?>> beans = new ConcurrentHashMap<>();
    HeldScope() { super(Held.class); }
    protected Map<BeanIdentifier, CreatedBean<?>> getScopeMap(boolean forCreation) { return beans; }
    public boolean isRunning() { return true; }
    public void close() { destroyScope(beans); }
}
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Around
@interface Wrapped {}
@Prototype @InterceptorBean(Wrapped.class)
class Advice implements MethodInterceptor<Object, Object> {
    public Object intercept(MethodInvocationContext<Object, Object> context) { return context.proceed(); }
    @PreDestroy void stop() { throw new AssertionError("advice failed"); }
}
@Held @Wrapped class Target {
    public String run() { return "ok"; }
    @PreDestroy void stop() { Log.events.add("target destroyed"); }
}
''')
        def type = ctx.classLoader.loadClass('test.Target')
        def log = ctx.classLoader.loadClass('test.Log')
        def scope = ctx.getBean(ctx.classLoader.loadClass('test.HeldScope'))
        def proxy = ctx.getBean(type)

        expect:
        proxy.run() == 'ok'
        !scope.beans.isEmpty()

        when:
        ctx.destroyBean(proxy)

        then:
        def failure = thrown(AssertionError)
        failure.message == 'advice failed'
        log.events == ['target destroyed']
        scope.beans.isEmpty()

        cleanup:
        ctx.close()
    }
}
