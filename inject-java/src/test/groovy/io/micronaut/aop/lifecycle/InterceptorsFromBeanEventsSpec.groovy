package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * A library finds the interceptors of a bean from the bean events: micronaut-jakarta-interceptors creates the
 * interceptor instances of an object as the object is created (section 2.3 of the Interceptors specification) from a
 * {@code BeanCreatedEventListener}, and destroys what is left of them from a {@code BeanDestroyedEventListener}.
 * Before 5.3 it read {@code Intercepted.$interceptorRegistrations()} off the proxy for both. A proxy retains nothing
 * since 5.3, and the non-singleton interceptors of a bean are its dependents instead, so both events report the
 * dependents of the bean: the ones it was created with, and the ones destroyed with it.
 */
class InterceptorsFromBeanEventsSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package eventregs;

import io.micronaut.aop.*;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.event.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {
}

// one instance for each bean it intercepts
@Prototype
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.PRE_DESTROY)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static final List<Object> INTERCEPTED_WITH = new ArrayList<>();
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        INTERCEPTED_WITH.add(this);
        return context.proceed();
    }
}

@Singleton
@Tracked
class TrackedBean {
    public String work() { return "done"; }
}

@Singleton
class OnCreate implements BeanCreatedEventListener<TrackedBean> {
    static final List<Object> FOUND = new ArrayList<>();
    @Override
    public TrackedBean onCreated(BeanCreatedEvent<TrackedBean> event) {
        for (BeanRegistration<?> registration : event.getDependentBeans()) {
            if (registration.getBean() instanceof TrackingInterceptor) {
                FOUND.add(registration.getBean());
            }
        }
        return event.getBean();
    }
}

@Singleton
class OnDestroy implements BeanDestroyedEventListener<TrackedBean> {
    static final List<Object> FOUND = new ArrayList<>();
    static final List<Object> REGISTERED = new ArrayList<>();
    @Override
    public void onDestroyed(BeanDestroyedEvent<TrackedBean> event) {
        for (BeanRegistration<?> registration : event.getDependentBeans()) {
            if (registration.getBean() instanceof TrackingInterceptor) {
                FOUND.add(registration.getBean());
            }
        }
        if (event.getBeanRegistration() != null) {
            REGISTERED.add(event.getBeanRegistration().getBean());
        }
    }
}
'''

    void 'test a BeanCreatedEventListener finds the interceptor of the bean among the dependents of the event'() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        def bean = context.getBean(context.classLoader.loadClass('eventregs.TrackedBean'))

        when:
        bean.work()
        def interceptedWith = context.classLoader.loadClass('eventregs.TrackingInterceptor').INTERCEPTED_WITH
        def found = context.classLoader.loadClass('eventregs.OnCreate').FOUND

        then: 'one interceptor instance intercepted the bean'
        !interceptedWith.isEmpty()
        interceptedWith.every { it.is(interceptedWith[0]) }

        and: 'and the listener was handed that instance'
        found.size() == 1
        found[0].is(interceptedWith[0])

        cleanup:
        context.close()
    }

    void 'test a BeanDestroyedEventListener finds the interceptor of the bean among the dependents of the event'() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        def bean = context.getBean(context.classLoader.loadClass('eventregs.TrackedBean'))
        bean.work()
        def interceptedWith = context.classLoader.loadClass('eventregs.TrackingInterceptor').INTERCEPTED_WITH
        def found = context.classLoader.loadClass('eventregs.OnDestroy').FOUND
        def registered = context.classLoader.loadClass('eventregs.OnDestroy').REGISTERED

        when:
        context.destroyBean(bean)

        then: 'the listener was handed the instance that intercepted the bean, pre-destroy included'
        interceptedWith.every { it.is(interceptedWith[0]) }
        found.size() == 1
        found[0].is(interceptedWith[0])

        and: 'and the registration the bean was destroyed through'
        registered.size() == 1
        registered[0].is(bean)

        cleanup:
        context.close()
    }
}
