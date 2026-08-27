package io.micronaut.aop.compile

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

class InterceptorTargetCompileSpec extends AbstractTypeElementSpec {

    void 'test prototype interceptor is created once per target and reused for its lifecycle'() {
        given:
        ApplicationContext context = buildContext('''
package targetscope;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@AroundConstruct
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {
}

@Prototype
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.PRE_DESTROY)
class TrackingInterceptor implements Interceptor<Object, Object> {
    static int instances;
    static final List<String> events = new ArrayList<>();
    static Class<?> targetType;

    private final int id = ++instances;

    TrackingInterceptor(InterceptorTarget target) {
        targetType = target.getType();
        events.add(id + ":CREATE");
    }

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        InterceptorKind kind = context instanceof ConstructorInvocationContext
            ? InterceptorKind.AROUND_CONSTRUCT
            : ((MethodInvocationContext<?, ?>) context).getKind();
        events.add(id + ":" + kind);
        return context.proceed();
    }

    @PreDestroy
    void destroy() {
        events.add(id + ":INTERCEPTOR_DESTROY");
    }
}

@Singleton
@Tracked
class MyBean {
    MyBean() {
        TrackingInterceptor.events.add("TARGET_CONSTRUCTOR");
    }

    @PostConstruct
    void init() {
        TrackingInterceptor.events.add("TARGET_POST_CONSTRUCT");
    }

    String work() {
        TrackingInterceptor.events.add("TARGET_METHOD");
        return "done";
    }

    @PreDestroy
    void close() {
        TrackingInterceptor.events.add("TARGET_PRE_DESTROY");
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('targetscope.TrackingInterceptor')
        Class<?> targetType = context.classLoader.loadClass('targetscope.MyBean')

        when:
        def bean = context.getBean(targetType)

        then: 'the interceptor is created with the target before construction and reused for post construct'
        interceptorType.instances == 1
        interceptorType.targetType == targetType
        interceptorType.events == [
            '1:CREATE',
            '1:AROUND_CONSTRUCT',
            'TARGET_CONSTRUCTOR',
            '1:POST_CONSTRUCT',
            'TARGET_POST_CONSTRUCT'
        ]

        when:
        assert bean.work() == 'done'

        then: 'the same interceptor handles business methods'
        interceptorType.instances == 1
        interceptorType.events[-2..-1] == ['1:AROUND', 'TARGET_METHOD']

        when:
        context.stop()

        then: 'the same interceptor handles pre destroy and is destroyed as a target dependency'
        interceptorType.instances == 1
        interceptorType.events[-3..-1] == [
            '1:PRE_DESTROY',
            'TARGET_PRE_DESTROY',
            '1:INTERCEPTOR_DESTROY'
        ]

        cleanup:
        context.close()
    }
}
