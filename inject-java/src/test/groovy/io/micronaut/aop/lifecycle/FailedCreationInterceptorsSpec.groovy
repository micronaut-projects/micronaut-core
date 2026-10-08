package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanCreationException

class FailedCreationInterceptorsSpec extends AbstractTypeElementSpec {

    void 'test the prototype interceptor of a bean whose #failure throws is destroyed'() {
        given:
        ApplicationContext context = buildContext('''
package failed.interceptor;

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

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        String kind = context instanceof ConstructorInvocationContext
            ? "AROUND_CONSTRUCT"
            : ((MethodInvocationContext<?, ?>) context).getKind().name();
        events.add(id + ":" + kind);
        return context.proceed();
    }

    @PreDestroy
    void destroy() {
        events.add(id + ":DESTROYED");
    }
}

@Singleton
@Tracked
class ConstructorFailing {
    ConstructorFailing() {
        throw new IllegalStateException("constructor failed");
    }

    String work() {
        return "done";
    }
}

@Singleton
@Tracked
class PostConstructFailing {
    @PostConstruct
    void init() {
        throw new IllegalStateException("post construct failed");
    }

    String work() {
        return "done";
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('failed.interceptor.TrackingInterceptor')

        when:
        context.getBean(context.classLoader.loadClass('failed.interceptor.' + bean))

        then:
        thrown(BeanCreationException)

        and: 'the one instance created for the bean is destroyed'
        interceptorType.instances == 1
        interceptorType.events == expected

        cleanup:
        context.close()

        where:
        bean                   | failure          | expected
        'ConstructorFailing'   | 'constructor'    | ['1:AROUND_CONSTRUCT', '1:DESTROYED']
        'PostConstructFailing' | 'post construct' | ['1:AROUND_CONSTRUCT', '1:POST_CONSTRUCT', '1:DESTROYED']
    }

    void 'test the prototype interceptor of a bean whose construction an interceptor rejects is destroyed'() {
        given:
        ApplicationContext context = buildContext('''
package failed.rejected;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.CONSTRUCTOR})
@AroundConstruct
@interface Rejected {
}

@Prototype
@InterceptorBean(Rejected.class)
class RejectingInterceptor implements ConstructorInterceptor<Object> {
    static final List<String> events = new ArrayList<>();

    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        events.add("AROUND_CONSTRUCT");
        throw new IllegalStateException("rejected");
    }

    @PreDestroy
    void destroy() {
        events.add("DESTROYED");
    }
}

@Singleton
@Rejected
class MyBean {
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('failed.rejected.RejectingInterceptor')

        when:
        context.getBean(context.classLoader.loadClass('failed.rejected.MyBean'))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'rejected'
        interceptorType.events == ['AROUND_CONSTRUCT', 'DESTROYED']

        cleanup:
        context.close()
    }
}
