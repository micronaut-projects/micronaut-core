package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * A bean created while a proxy selects the interceptors of its target, such as a dependency of an interceptor bound
 * only for {@code AROUND}, gets interceptors of its own, not the ones the target was created with.
 */
class ProxyTargetNestedBeanInterceptorsSpec extends AbstractTypeElementSpec {

    void 'test a dependency of a late interceptor is constructed with its own interceptor'() {
        given:
        ApplicationContext context = buildContext('''
package nestedowner;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@AroundConstruct
@interface Tracked {
}

@Prototype
@InterceptorBean(Tracked.class)
class TrackingInterceptor implements ConstructorInterceptor<Object> {
    static final List<String> EVENTS = new ArrayList<>();
    static int instances;
    final int id = ++instances;

    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        EVENTS.add(context.getDeclaringType().getSimpleName() + ":" + id);
        return context.proceed();
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Around
@interface Late {
}

@Prototype
@Tracked
class Helper {
}

@Prototype
@InterceptorBean(Late.class)
class LateInterceptor implements MethodInterceptor<Object, Object> {
    final Helper helper;

    LateInterceptor(Helper helper) {
        this.helper = helper;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed();
    }
}

@Singleton
@Tracked
@Around(proxyTarget = true, lazy = true, lazyInterceptorsPerTarget = true)
class TrackedBean {
    @Late
    public String call() {
        return "called";
    }
}
''')
        def bean = context.getBean(context.classLoader.loadClass('nestedowner.TrackedBean'))

        when:
        def result = bean.call()

        then: 'the helper is constructed with a new instance, not the one of the target'
        result == 'called'
        context.classLoader.loadClass('nestedowner.TrackingInterceptor').EVENTS == ['TrackedBean:1', 'Helper:2']

        cleanup:
        context.close()
    }
}
