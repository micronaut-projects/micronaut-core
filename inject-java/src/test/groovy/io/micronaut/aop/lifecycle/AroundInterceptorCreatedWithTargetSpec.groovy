package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.InterceptedProxy
import io.micronaut.context.ApplicationContext

/**
 * A non-singleton interceptor bound for {@code AROUND} alone is created with the bean it intercepts, whatever proxies
 * that bean, and is among the dependents the creation event reports. Section 2.3 of the Interceptors specification
 * creates the interceptor instances of a target when the target is created, and a library that honours it
 * (micronaut-jakarta-interceptors) does so from the {@code BeanCreatedEvent} of the bean.
 */
class AroundInterceptorCreatedWithTargetSpec extends AbstractTypeElementSpec {

    private static String source(String pkg, String around, String bindingOnClass, String bindingOnMethod) {
        """
package ${pkg};

import io.micronaut.aop.*;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.event.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
${around}
@interface Tracked {
}

// one instance for each bean it intercepts, bound for AROUND alone
@Prototype
@InterceptorBinding(Tracked.class)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static final List<Object> CREATED = new ArrayList<>();
    static final List<Object> INTERCEPTED_WITH = new ArrayList<>();
    TrackingInterceptor() { CREATED.add(this); }
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        INTERCEPTED_WITH.add(this);
        return context.proceed();
    }
}

@Singleton
${bindingOnClass}
class TrackedBean {
    ${bindingOnMethod}
    public String work() { return "done"; }
}

@Singleton
class OnCreated implements BeanCreatedEventListener<TrackedBean> {
    // what the creation event of the bean reported, the proxy fronting a target left out: its events are the target's
    static final List<Object> FOUND = new ArrayList<>();
    static final List<Object> CREATED_BEFORE = new ArrayList<>();
    @Override
    public TrackedBean onCreated(BeanCreatedEvent<TrackedBean> event) {
        if (!(event.getBean() instanceof InterceptedProxy)) {
            CREATED_BEFORE.addAll(TrackingInterceptor.CREATED);
            for (BeanRegistration<?> registration : event.getDependentBeans()) {
                if (registration.getBean() instanceof TrackingInterceptor) {
                    FOUND.add(registration.getBean());
                }
            }
        }
        return event.getBean();
    }
}
"""
    }

    void 'test an AROUND interceptor of a #description is created with the bean and reported by its creation event'() {
        given:
        ApplicationContext context = buildContext(source(pkg, around, onClass, onMethod))
        def bean = context.getBean(context.classLoader.loadClass(pkg + '.TrackedBean'))
        def interceptor = context.classLoader.loadClass(pkg + '.TrackingInterceptor')
        def listener = context.classLoader.loadClass(pkg + '.OnCreated')

        when: 'the target exists, which for a lazy proxy is on the first call'
        if (bean instanceof InterceptedProxy) {
            bean.interceptedTarget()
        }

        then: 'the interceptor was created with it, before the creation event'
        interceptor.CREATED.size() == 1
        listener.CREATED_BEFORE == interceptor.CREATED

        and: 'the creation event reported it among the dependents of the bean'
        listener.FOUND.size() == 1
        listener.FOUND[0].is(interceptor.CREATED[0])

        when:
        bean.work()
        bean.work()

        then: 'it is the instance that intercepts'
        interceptor.CREATED.size() == 1
        interceptor.INTERCEPTED_WITH.size() == 2
        interceptor.INTERCEPTED_WITH.every { it.is(interceptor.CREATED[0]) }

        cleanup:
        context.close()

        where:
        description                               | pkg                  | around                                    | onClass    | onMethod
        'subclass proxy bound on the class'       | 'aroundonly.subcls'  | '@Around'                                 | '@Tracked' | ''
        'subclass proxy bound on a method'        | 'aroundonly.submeth' | '@Around'                                 | ''         | '@Tracked'
        'proxy target bound on the class'         | 'aroundonly.tgtcls'  | '@Around(proxyTarget = true)'             | '@Tracked' | ''
        'proxy target bound on a method'          | 'aroundonly.tgtmeth' | '@Around(proxyTarget = true)'             | ''         | '@Tracked'
        'lazy proxy target bound on the class'    | 'aroundonly.lazycls' | '@Around(proxyTarget = true, lazy = true)' | '@Tracked' | ''
        'hot-swappable proxy target on the class' | 'aroundonly.hotcls'  | '@Around(proxyTarget = true, hotswap = true)' | '@Tracked' | ''
    }
}
