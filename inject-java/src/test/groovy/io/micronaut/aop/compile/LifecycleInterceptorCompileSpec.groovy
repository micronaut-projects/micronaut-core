package io.micronaut.aop.compile

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

class LifecycleInterceptorCompileSpec extends AbstractTypeElementSpec {

    void 'test lifecycle infrastructure preserves the user interface as the primary proxy interface'() {
        given:
        ApplicationContext context = buildContext('''
package lifecycleinterface;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Context;
import java.lang.annotation.*;

@Context
@TrackedIntroduction
interface MyApi {
    String name();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Introduction
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface TrackedIntroduction {
}

@Context
@InterceptorBinding(value = TrackedIntroduction.class, kind = InterceptorKind.INTRODUCTION)
@InterceptorBinding(value = TrackedIntroduction.class, kind = InterceptorKind.POST_CONSTRUCT)
class IntroductionInterceptor implements Interceptor<Object, Object> {
    static int instances;
    static int postConstructCalls;
    static int introductionCalls;

    IntroductionInterceptor() {
        instances++;
    }

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        InterceptorKind kind = ((MethodInvocationContext<?, ?>) context).getKind();
        if (kind == InterceptorKind.POST_CONSTRUCT) {
            postConstructCalls++;
            return context.proceed();
        }
        introductionCalls++;
        return "target";
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('lifecycleinterface.IntroductionInterceptor')
        Class<?> targetType = context.classLoader.loadClass('lifecycleinterface.MyApi')

        when:
        def bean = context.getBean(targetType)

        then:
        bean.class.interfaces.first() == targetType
        bean.name() == 'target'
        interceptorType.instances == 1
        interceptorType.postConstructCalls == 1
        interceptorType.introductionCalls == 1

        cleanup:
        context.close()
    }

    void 'test prototype interceptor is created once per target and reused for its lifecycle'() {
        given:
        ApplicationContext context = buildContext('''
package lifecycleretention;

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

    private final int id = ++instances;

    TrackingInterceptor() {
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
        Class<?> interceptorType = context.classLoader.loadClass('lifecycleretention.TrackingInterceptor')
        Class<?> targetType = context.classLoader.loadClass('lifecycleretention.MyBean')

        when:
        def bean = context.getBean(targetType)

        then: 'the interceptor is created before construction and reused for post construct'
        interceptorType.instances == 1
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

    void 'test lifecycle-only prototype interceptor is retained when around advice uses a different binding'() {
        given:
        ApplicationContext context = buildContext('''
package lifecyclebindings;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Around
@AroundConstruct
@interface WorkAdvice {
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface LifecycleAdvice {
}

@Prototype
@InterceptorBinding(value = LifecycleAdvice.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = LifecycleAdvice.class, kind = InterceptorKind.PRE_DESTROY)
class LifecycleInterceptor implements Interceptor<Object, Object> {
    static int instances;
    static final List<String> events = new ArrayList<>();
    private final int id = ++instances;

    LifecycleInterceptor() {
        events.add(id + ":CREATE");
    }

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        InterceptorKind kind = ((MethodInvocationContext<?, ?>) context).getKind();
        events.add(id + ":" + kind);
        return context.proceed();
    }

    @PreDestroy
    void destroy() {
        events.add(id + ":DESTROY");
    }
}

@Singleton
@WorkAdvice
@LifecycleAdvice
class MyBean {
    @PostConstruct
    void init() {
        LifecycleInterceptor.events.add("TARGET_POST_CONSTRUCT");
    }

    @PreDestroy
    void close() {
        LifecycleInterceptor.events.add("TARGET_PRE_DESTROY");
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('lifecyclebindings.LifecycleInterceptor')
        Class<?> targetType = context.classLoader.loadClass('lifecyclebindings.MyBean')

        when:
        context.getBean(targetType)

        then:
        interceptorType.instances == 1
        interceptorType.events == ['1:CREATE', '1:POST_CONSTRUCT', 'TARGET_POST_CONSTRUCT']

        when:
        context.stop()

        then:
        interceptorType.events[-3..-1] == ['1:PRE_DESTROY', 'TARGET_PRE_DESTROY', '1:DESTROY']

        cleanup:
        context.close()
    }

    void 'test each prototype target receives a different prototype interceptor'() {
        given:
        ApplicationContext context = buildContext('''
package lifecycleprototypes;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Around
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}

@Prototype
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements Interceptor<Object, Object> {
    static int instances;
    private final int id = ++instances;

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        Object result = context.proceed();
        if (((MethodInvocationContext<?, ?>) context).getKind() == InterceptorKind.POST_CONSTRUCT) {
            ((MyBean) context.getTarget()).interceptorId = id;
        }
        return result;
    }
}

@Prototype
@Tracked
class MyBean {
    int interceptorId;

    int interceptorId() {
        return interceptorId;
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('lifecycleprototypes.TrackingInterceptor')
        Class<?> targetType = context.classLoader.loadClass('lifecycleprototypes.MyBean')

        when:
        def first = context.getBean(targetType)
        def second = context.getBean(targetType)

        then:
        !first.is(second)
        first.interceptorId() == 1
        second.interceptorId() == 2
        interceptorType.instances == 2

        cleanup:
        context.close()
    }
}
