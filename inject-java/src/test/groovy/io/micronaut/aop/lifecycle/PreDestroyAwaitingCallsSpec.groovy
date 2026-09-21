package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * A bean's {@code @PreDestroy} commonly waits for the work still running on other threads, and that work may call the
 * bean through its proxy. The call must not wait for the destruction that is waiting for it.
 */
class PreDestroyAwaitingCallsSpec extends AbstractTypeElementSpec {

    void 'test a pre-destroy method that waits for a call on another thread through a #description completes'() {
        given:
        ApplicationContext context = buildContext("""
package ${pkg};

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
${around}
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {
}

@Prototype
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.PRE_DESTROY)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed();
    }
}

@Singleton
@Tracked
class Worker {
    // the bean as its callers hold it, set by the test
    static volatile Object proxy;
    static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    public String work() { return "done"; }

    @PreDestroy
    void close() throws Exception {
        // the work still in flight calls the bean, and the destruction waits for it
        Future<?> call = Executors.newSingleThreadExecutor().submit(() -> ((Worker) proxy).work());
        try {
            EVENTS.add("call " + call.get(5, TimeUnit.SECONDS));
        } catch (TimeoutException e) {
            EVENTS.add("call blocked");
        }
    }
}
""")
        def type = context.classLoader.loadClass(pkg + '.Worker')
        def bean = context.getBean(type)
        type.proxy = bean
        bean.work()

        when: "the context closes, which destroys the target of a proxy as well"
        context.stop()

        then:
        type.EVENTS.toList() == ["call done"]

        cleanup:
        context.close()

        where:
        description      | pkg                   | around
        'subclass proxy' | 'predestroyawait.sub' | '@Around'
        'proxy target'   | 'predestroyawait.tgt' | '@Around(proxyTarget = true)'
    }
}
