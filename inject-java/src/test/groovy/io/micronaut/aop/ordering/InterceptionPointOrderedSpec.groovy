package io.micronaut.aop.ordering

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * One interceptor bean placed differently at different interception points through
 * {@link io.micronaut.aop.InterceptionPointOrdered}.
 */
class InterceptionPointOrderedSpec extends AbstractTypeElementSpec {

    void 'test one interceptor runs before phased advice on one point and after it on another'() {
        given:
        ApplicationContext context = buildContext('''
package ordering.point;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.type.Executable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
@Around
@AroundConstruct
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Advised {
}

/** Marks the points where the point ordered interceptor asks to run before the phased one. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@interface Early {
}

class Events {
    static final List<String> LOG = new ArrayList<>();
}

abstract class Base implements MethodInterceptor<Object, Object>, ConstructorInterceptor<Object> {
    abstract String id();

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        if (context instanceof ConstructorInvocationContext<Object> constructorContext) {
            return intercept(constructorContext);
        }
        return intercept((MethodInvocationContext<Object, Object>) context);
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Events.LOG.add(context.getKind().name() + ":" + context.getMethodName() + ":" + id());
        return context.proceed();
    }

    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        Events.LOG.add("AROUND_CONSTRUCT:" + id());
        return context.proceed();
    }
}

/** Stands for core advice: always in a phase of io.micronaut.aop.InterceptPhase. */
@Singleton
@InterceptorBinding(value = Advised.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Advised.class, kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(value = Advised.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Advised.class, kind = InterceptorKind.PRE_DESTROY)
class Phased extends Base {
    String id() { return "phased"; }
    public int getOrder() { return InterceptPhase.CACHE.getPosition(); }
}

/** Runs after the phased advice, except at the methods marked @Early and at the constructor and post-construct points. */
@Prototype
@InterceptorBinding(value = Advised.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Advised.class, kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(value = Advised.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Advised.class, kind = InterceptorKind.PRE_DESTROY)
class PointOrdered extends Base implements InterceptionPointOrdered {
    static final List<String> ASKED = new ArrayList<>();

    String id() { return "point"; }

    public int getOrder() { return 2000; }

    @Override
    public int getOrder(Executable<?, ?> method, InterceptorKind kind) {
        ASKED.add(kind.name() + ":" + ((io.micronaut.inject.ExecutableMethod<?, ?>) method).getMethodName());
        // a lifecycle point is the event of the bean, so it is placed by its kind
        boolean early = kind == InterceptorKind.POST_CONSTRUCT
            || kind == InterceptorKind.AROUND && method.getAnnotationMetadata().hasAnnotation(Early.class);
        return early ? InterceptPhase.VALIDATE.getPosition() - 1 : getOrder();
    }

    @Override
    public int getOrder(BeanConstructor<?> constructor) {
        ASKED.add("AROUND_CONSTRUCT");
        return constructor.getAnnotationMetadata().hasAnnotation(Early.class) ? InterceptPhase.VALIDATE.getPosition() - 1 : getOrder();
    }
}

@Singleton
@Advised
class MyBean {
    @Early
    MyBean() {}

    @PostConstruct
    void init() {}

    @Early
    String early() { return "early"; }

    String late() { return "late"; }

    @PreDestroy
    void close() {}
}
''')
        Class<?> events = context.classLoader.loadClass('ordering.point.Events')
        Class<?> pointOrdered = context.classLoader.loadClass('ordering.point.PointOrdered')

        when:
        def bean = context.getBean(context.classLoader.loadClass('ordering.point.MyBean'))
        bean.early()
        bean.late()
        context.stop()
        List<String> log = events.LOG

        then: 'the point ordered interceptor runs first where the point is marked and last everywhere else'
        log.findAll { it.startsWith('AROUND_CONSTRUCT') } == ['AROUND_CONSTRUCT:point', 'AROUND_CONSTRUCT:phased']
        log.findAll { it.startsWith('POST_CONSTRUCT') } == ['POST_CONSTRUCT:init:point', 'POST_CONSTRUCT:init:phased']
        log.findAll { it.startsWith('AROUND:early') } == ['AROUND:early:point', 'AROUND:early:phased']
        log.findAll { it.startsWith('AROUND:late') } == ['AROUND:late:phased', 'AROUND:late:point']
        log.findAll { it.startsWith('PRE_DESTROY') } == ['PRE_DESTROY:close:phased', 'PRE_DESTROY:close:point']

        and: 'the order was asked once for each interception point, with its kind'
        List<String> asked = pointOrdered.ASKED
        asked.count('AROUND_CONSTRUCT') == 1
        asked.count('POST_CONSTRUCT:init') == 1
        asked.count('AROUND:early') == 1
        asked.count('AROUND:late') == 1
        asked.count('PRE_DESTROY:close') == 1

        cleanup:
        context.close()
    }

    void 'test the order of an interceptor that does not report one for the point is unchanged'() {
        given:
        ApplicationContext context = buildContext('''
package ordering.defaulted;

import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@interface Advised {
}

class Events {
    static final List<String> LOG = new ArrayList<>();
}

@Singleton
@InterceptorBinding(value = Advised.class, kind = InterceptorKind.AROUND)
class Phased implements MethodInterceptor<Object, Object> {
    public int getOrder() { return InterceptPhase.CACHE.getPosition(); }
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Events.LOG.add("phased");
        return context.proceed();
    }
}

/** Implements the interface but reports nothing for the point, so Ordered.getOrder() places it. */
@Singleton
@InterceptorBinding(value = Advised.class, kind = InterceptorKind.AROUND)
class Defaulted implements MethodInterceptor<Object, Object>, InterceptionPointOrdered {
    public int getOrder() { return InterceptPhase.VALIDATE.getPosition(); }
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Events.LOG.add("defaulted");
        return context.proceed();
    }
}

@Singleton
@Advised
class MyBean {
    String work() { return "done"; }
}
''')
        Class<?> events = context.classLoader.loadClass('ordering.defaulted.Events')

        when:
        context.getBean(context.classLoader.loadClass('ordering.defaulted.MyBean')).work()

        then:
        events.LOG == ['defaulted', 'phased']

        cleanup:
        context.close()
    }
}
