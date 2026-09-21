package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * A library built against 5.2 finds the interceptors of a bean from the bean events by reading
 * {@code Intercepted.$interceptorRegistrations()} off the proxy: micronaut-jakarta-interceptors creates the interceptor
 * instances of an object as the object is created (section 2.3 of the Interceptors specification) from a
 * {@code BeanCreatedEventListener}, and destroys what is left of them from a {@code BeanDestroyedEventListener}.
 * A proxy generated since 5.3 retains nothing, so both listeners find no interceptor at all and the library silently
 * stops doing either.
 */
class InterceptorRegistrationsFromBeanEventsSpec extends AbstractTypeElementSpec {

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
class RetainedOnCreate implements BeanCreatedEventListener<TrackedBean> {
    static final List<Object> FOUND = new ArrayList<>();
    @Override
    @SuppressWarnings("removal")
    public TrackedBean onCreated(BeanCreatedEvent<TrackedBean> event) {
        for (BeanRegistration<Interceptor<?, ?>> registration : ((Intercepted) event.getBean()).$interceptorRegistrations()) {
            FOUND.add(registration.getBean());
        }
        return event.getBean();
    }
}

@Singleton
class DependentsOnCreate implements BeanCreatedEventListener<TrackedBean> {
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
class RetainedOnDestroy implements BeanDestroyedEventListener<TrackedBean> {
    static final List<Object> FOUND = new ArrayList<>();
    @Override
    @SuppressWarnings("removal")
    public void onDestroyed(BeanDestroyedEvent<TrackedBean> event) {
        for (BeanRegistration<Interceptor<?, ?>> registration : ((Intercepted) event.getBean()).$interceptorRegistrations()) {
            FOUND.add(registration.getBean());
        }
    }
}
'''

    void 'test a BeanCreatedEventListener finds the interceptor of the bean through $interceptorRegistrations()'() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        def bean = context.getBean(context.classLoader.loadClass('eventregs.TrackedBean'))

        when:
        bean.work()
        def interceptedWith = context.classLoader.loadClass('eventregs.TrackingInterceptor').INTERCEPTED_WITH
        def found = context.classLoader.loadClass('eventregs.RetainedOnCreate').FOUND

        then: 'one interceptor instance intercepted the bean'
        !interceptedWith.isEmpty()
        interceptedWith.every { it.is(interceptedWith[0]) }

        and: 'and the listener was handed that instance'
        found.size() == 1
        found[0].is(interceptedWith[0])

        cleanup:
        context.close()
    }

    void 'test a BeanDestroyedEventListener finds the interceptor of the bean through $interceptorRegistrations()'() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        def bean = context.getBean(context.classLoader.loadClass('eventregs.TrackedBean'))
        bean.work()
        def interceptedWith = context.classLoader.loadClass('eventregs.TrackingInterceptor').INTERCEPTED_WITH
        def found = context.classLoader.loadClass('eventregs.RetainedOnDestroy').FOUND

        when:
        context.destroyBean(bean)

        then: 'the listener was handed the instance that intercepted the bean, pre-destroy included'
        interceptedWith.every { it.is(interceptedWith[0]) }
        found.size() == 1
        found[0].is(interceptedWith[0])

        cleanup:
        context.close()
    }

    void 'test a BeanCreatedEventListener finds the interceptor of the bean among the dependents of the event'() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        def bean = context.getBean(context.classLoader.loadClass('eventregs.TrackedBean'))

        when:
        bean.work()
        def interceptedWith = context.classLoader.loadClass('eventregs.TrackingInterceptor').INTERCEPTED_WITH
        def found = context.classLoader.loadClass('eventregs.DependentsOnCreate').FOUND

        then: 'the route that needs no retained list still works'
        found.size() == 1
        found[0].is(interceptedWith[0])

        cleanup:
        context.close()
    }
}
