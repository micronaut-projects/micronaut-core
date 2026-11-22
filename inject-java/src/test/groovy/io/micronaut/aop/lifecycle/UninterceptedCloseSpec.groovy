package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanDestructionException

/**
 * A container that closes a bean as it destroys it does so between the bean's {@code PreDestroy} methods and the
 * release of its dependents, registered with {@code BeanPreDestroyEvent#afterPreDestroy}, and calls the method past
 * the around advice of the subclass generated for the bean with {@code BeanDefinition#findUninterceptedMethod}, while
 * a client's call of the same method is still intercepted.
 */
class UninterceptedCloseSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package uninterceptedclose;

import io.micronaut.aop.*;
import io.micronaut.context.LifeCycle;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Order;
import io.micronaut.context.event.*;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

class Log {
    static final List<String> events = new CopyOnWriteArrayList<>();
    static volatile boolean failAction;
    static volatile boolean failListener;
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.AROUND)
@interface Watched {
}

@Singleton
@InterceptorBinding(value = Watched.class, kind = InterceptorKind.AROUND)
class WatchingInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Log.events.add("intercepted " + context.getMethodName());
        return context.proceed();
    }
}

@Prototype
class Dependency {
    @PreDestroy
    void destroy() { Log.events.add("dependency destroyed"); }
}

@Watched
@Prototype
class WatchedResource implements AutoCloseable {
    @Inject Dependency dependency;

    public String name() { return "watched"; }

    @PreDestroy
    void destroy() { Log.events.add("preDestroy"); }

    @Override
    public void close() { Log.events.add("close"); }
}

@Prototype
class PlainResource implements AutoCloseable {
    @Inject Dependency dependency;

    @PreDestroy
    void destroy() { Log.events.add("preDestroy"); }

    @Override
    public void close() { Log.events.add("close"); }
}

@Singleton
class Closer implements BeanPreDestroyEventListener<AutoCloseable>, BeanDestroyedEventListener<AutoCloseable> {
    @Override
    public AutoCloseable onPreDestroy(BeanPreDestroyEvent<AutoCloseable> event) {
        AutoCloseable bean = event.getBean();
        BeanDefinition<AutoCloseable> definition = event.getBeanDefinition();
        Log.events.add("listener");
        event.afterPreDestroy(() -> {
            Log.events.add("action");
            if (Log.failAction) {
                throw new IllegalStateException("cannot close");
            }
            Optional<ExecutableMethod<AutoCloseable, Object>> close = definition.findUninterceptedMethod("close");
            if (close.isPresent()) {
                close.get().invoke(bean);
            } else {
                bean.close();
            }
        });
        event.afterPreDestroy(() -> Log.events.add("second action"));
        return bean;
    }

    @Override
    public void onDestroyed(BeanDestroyedEvent<AutoCloseable> event) {
        Log.events.add("destroyed");
    }
}

@Singleton
@Order(1)
class FailingListener implements BeanPreDestroyEventListener<AutoCloseable> {
    @Override
    public AutoCloseable onPreDestroy(BeanPreDestroyEvent<AutoCloseable> event) {
        if (Log.failListener) {
            throw new IllegalStateException("listener failed");
        }
        return event.getBean();
    }
}

@Prototype
class LifeCycleResource implements AutoCloseable, LifeCycle<LifeCycleResource> {
    @Inject Dependency dependency;

    @Override
    public boolean isRunning() { return true; }

    @Override
    public LifeCycleResource stop() {
        Log.events.add("stop");
        return this;
    }

    @PreDestroy
    void destroy() { Log.events.add("preDestroy"); }

    @Override
    public void close() { Log.events.add("close"); }
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around(proxyTarget = true)
@interface WatchedTarget {
}

@Singleton
@InterceptorBean(WatchedTarget.class)
class TargetWatchingInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Log.events.add("intercepted target " + context.getMethodName());
        return context.proceed();
    }
}

@WatchedTarget
@Prototype
class ProxiedResource implements AutoCloseable {
    @Override
    public void close() { Log.events.add("close"); }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Introduction
@interface Introduced {
}

@Singleton
@InterceptorBean(Introduced.class)
class IntroducingInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Log.events.add("introduced " + context.getMethodName());
        return null;
    }
}

@Introduced
@Prototype
interface IntroducedResource extends AutoCloseable {
    String name();
}
'''

    private ApplicationContext context

    def setup() {
        context = buildContext(SOURCE)
        log.clear()
        def logType = context.classLoader.loadClass('uninterceptedclose.Log')
        logType.failAction = false
        logType.failListener = false
    }

    def cleanup() {
        context?.close()
    }

    private List<String> getLog() {
        context.classLoader.loadClass('uninterceptedclose.Log').events
    }

    void "the uninterceptedmethod of an advised bean runs past its interceptors, a client's call is intercepted"() {
        given:
        def type = context.classLoader.loadClass('uninterceptedclose.WatchedResource')
        def definition = context.getBeanDefinition(type)
        def bean = context.getBean(type)

        when:
        bean.close()
        definition.findMethod('close').get().invoke(bean)

        then:
        log == ['intercepted close', 'close', 'intercepted close', 'close']

        when:
        log.clear()
        definition.findUninterceptedMethod('close').get().invoke(bean)
        def name = definition.findUninterceptedMethod('name').get().invoke(bean)

        then:
        name == 'watched'
        log == ['close']

        when: "the instance is still intercepted afterwards"
        log.clear()
        bean.close()

        then:
        log == ['intercepted close', 'close']
    }

    void "the uninterceptedmethod of a bean without advice is the method findMethod finds"() {
        given:
        def type = context.classLoader.loadClass('uninterceptedclose.Dependency')
        def definition = context.getBeanDefinition(type)

        expect:
        definition.findUninterceptedMethod('destroy').isPresent() == definition.findMethod('destroy').isPresent()
        !definition.findUninterceptedMethod('missing').isPresent()
    }

    void "an action registered with afterPreDestroy runs after the PreDestroy methods and before the dependents are released - #description"() {
        given:
        def registration = context.createDependencyGroup()
            .createBeanRegistration(context.getBeanDefinition(context.classLoader.loadClass('uninterceptedclose.' + simpleName)))

        when:
        context.destroyBean(registration)

        then:
        log == ['listener', 'preDestroy', 'action', 'close', 'second action', 'dependency destroyed', 'destroyed']

        where:
        description            | simpleName
        'an advised bean'      | 'WatchedResource'
        'a bean without advice'| 'PlainResource'
    }

    void "a failing action fails the destruction after the remaining actions ran, and the dependents are released"() {
        given:
        def registration = context.createDependencyGroup()
            .createBeanRegistration(context.getBeanDefinition(context.classLoader.loadClass('uninterceptedclose.WatchedResource')))
        context.classLoader.loadClass('uninterceptedclose.Log').failAction = true

        when:
        context.destroyBean(registration)

        then:
        def e = thrown(BeanDestructionException)
        e.cause instanceof IllegalStateException
        e.cause.message == 'cannot close'
        log == ['listener', 'preDestroy', 'action', 'second action', 'dependency destroyed']
    }

    void "a failing action does not prevent the bean from being stopped"() {
        given:
        def registration = context.createDependencyGroup()
            .createBeanRegistration(context.getBeanDefinition(context.classLoader.loadClass('uninterceptedclose.LifeCycleResource')))
        context.classLoader.loadClass('uninterceptedclose.Log').failAction = true

        when:
        context.destroyBean(registration)

        then:
        def e = thrown(BeanDestructionException)
        e.cause.message == 'cannot close'
        // the definition stops a life cycle bean before its PreDestroy methods, and the context once more after them
        log == ['listener', 'stop', 'preDestroy', 'action', 'second action', 'stop', 'dependency destroyed']
    }

    void "a failing listener cancels the PreDestroy methods and the actions registered before it"() {
        given:
        def registration = context.createDependencyGroup()
            .createBeanRegistration(context.getBeanDefinition(context.classLoader.loadClass('uninterceptedclose.PlainResource')))
        context.classLoader.loadClass('uninterceptedclose.Log').failListener = true

        when:
        context.destroyBean(registration)

        then:
        def e = thrown(BeanDestructionException)
        e.cause.message == 'listener failed'
        log == ['listener', 'dependency destroyed']
    }

    void "no uninterceptedmethod is found for a proxy of a target, whose instances run none of their own"() {
        given:
        def definition = context.getBeanDefinition(context.classLoader.loadClass('uninterceptedclose.ProxiedResource'))

        expect:
        definition instanceof io.micronaut.inject.ProxyBeanDefinition
        definition.findMethod('close').isPresent()
        !definition.findUninterceptedMethod('close').isPresent()
    }

    void "no uninterceptedmethod is found for a method introduction advice implements"() {
        given:
        def type = context.classLoader.loadClass('uninterceptedclose.IntroducedResource')
        def definition = context.getBeanDefinition(type)

        expect: "the methods are executable, and run the introduction advice however they are invoked"
        definition.findMethod('close').isPresent()
        definition.findMethod('name').isPresent()
        !definition.findUninterceptedMethod('close').isPresent()
        !definition.findUninterceptedMethod('name').isPresent()
    }
}
