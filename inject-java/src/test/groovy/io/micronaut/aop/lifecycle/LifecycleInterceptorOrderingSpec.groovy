package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * Interceptor selection and ordering for beans that combine around advice with lifecycle advice.
 *
 * Reusing the interceptor instances a bean already owns changes where the candidate set comes from, so these
 * cover the properties that set has to keep: the order interceptors run in, the kinds they are selected for,
 * and the fact that they never leak between beans.
 */
class LifecycleInterceptorOrderingSpec extends AbstractTypeElementSpec {

    void 'test interceptor order is honoured in every phase of a proxied bean'() {
        given:
        ApplicationContext context = buildContext('''
package ordering.proxied;

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

class Events {
    static final List<String> LOG = new ArrayList<>();
}

abstract class Base implements Interceptor<Object, Object>, io.micronaut.core.order.Ordered {
    abstract String id();

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        String kind = context instanceof ConstructorInvocationContext
            ? "AROUND_CONSTRUCT"
            : ((MethodInvocationContext<?, ?>) context).getKind().name();
        Events.LOG.add(kind + ":" + id());
        return context.proceed();
    }
}

@Prototype
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.PRE_DESTROY)
class First extends Base {
    String id() { return "first"; }
    public int getOrder() { return 10; }
}

@Prototype
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.PRE_DESTROY)
class Second extends Base {
    String id() { return "second"; }
    public int getOrder() { return 20; }
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.PRE_DESTROY)
class Third extends Base {
    String id() { return "third"; }
    public int getOrder() { return 30; }
}

@Singleton
@Tracked
class MyBean {
    @PostConstruct void init() {}
    String work() { return "done"; }
    @PreDestroy void close() {}
}
''')
        Class<?> events = context.classLoader.loadClass('ordering.proxied.Events')

        when:
        def bean = context.getBean(context.classLoader.loadClass('ordering.proxied.MyBean'))
        bean.work()
        context.stop()
        def log = events.LOG

        then: 'every phase runs all three in declared order'
        log.findAll { it.startsWith('AROUND_CONSTRUCT') } == ['AROUND_CONSTRUCT:first', 'AROUND_CONSTRUCT:second', 'AROUND_CONSTRUCT:third']
        log.findAll { it.startsWith('POST_CONSTRUCT') } == ['POST_CONSTRUCT:first', 'POST_CONSTRUCT:second', 'POST_CONSTRUCT:third']
        log.findAll { it.startsWith('AROUND:') } == ['AROUND:first', 'AROUND:second', 'AROUND:third']
        log.findAll { it.startsWith('PRE_DESTROY') } == ['PRE_DESTROY:first', 'PRE_DESTROY:second', 'PRE_DESTROY:third']

        cleanup:
        context.close()
    }

    void 'test interceptor order is honoured in every phase without an around proxy'() {
        given:
        ApplicationContext context = buildContext('''
package ordering.noproxy;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@AroundConstruct
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Managed {
}

class Events {
    static final List<String> LOG = new ArrayList<>();
}

abstract class Base implements Interceptor<Object, Object>, io.micronaut.core.order.Ordered {
    abstract String id();

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        String kind = context instanceof ConstructorInvocationContext
            ? "AROUND_CONSTRUCT"
            : ((MethodInvocationContext<?, ?>) context).getKind().name();
        Events.LOG.add(kind + ":" + id());
        return context.proceed();
    }
}

@Prototype
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.PRE_DESTROY)
class Alpha extends Base {
    String id() { return "alpha"; }
    public int getOrder() { return 1; }
}

@Prototype
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.PRE_DESTROY)
class Beta extends Base {
    String id() { return "beta"; }
    public int getOrder() { return 2; }
}

@Singleton
@Managed
class MyBean {
    @PostConstruct void init() {}
    @PreDestroy void close() {}
}
''')
        Class<?> events = context.classLoader.loadClass('ordering.noproxy.Events')

        when:
        context.getBean(context.classLoader.loadClass('ordering.noproxy.MyBean'))
        context.stop()
        def log = events.LOG

        then:
        log.findAll { it.startsWith('AROUND_CONSTRUCT') } == ['AROUND_CONSTRUCT:alpha', 'AROUND_CONSTRUCT:beta']
        log.findAll { it.startsWith('POST_CONSTRUCT') } == ['POST_CONSTRUCT:alpha', 'POST_CONSTRUCT:beta']
        log.findAll { it.startsWith('PRE_DESTROY') } == ['PRE_DESTROY:alpha', 'PRE_DESTROY:beta']

        cleanup:
        context.close()
    }

    void 'test an interceptor bound to one kind only runs for that kind'() {
        given:
        ApplicationContext context = buildContext('''
package ordering.kinds;

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

class Events {
    static final List<String> LOG = new ArrayList<>();
}

abstract class Base implements Interceptor<Object, Object>, io.micronaut.core.order.Ordered {
    abstract String id();

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        String kind = context instanceof ConstructorInvocationContext
            ? "AROUND_CONSTRUCT"
            : ((MethodInvocationContext<?, ?>) context).getKind().name();
        Events.LOG.add(id() + "@" + kind);
        return context.proceed();
    }
}

@Prototype
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND)
class AroundOnly extends Base {
    String id() { return "around"; }
}

@Prototype
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class PostOnly extends Base {
    String id() { return "post"; }
}

@Prototype
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.PRE_DESTROY)
class PreOnly extends Base {
    String id() { return "pre"; }
}

@Prototype
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND_CONSTRUCT)
class ConstructOnly extends Base {
    String id() { return "construct"; }
}

@Singleton
@Tracked
class MyBean {
    @PostConstruct void init() {}
    String work() { return "done"; }
    @PreDestroy void close() {}
}
''')
        Class<?> events = context.classLoader.loadClass('ordering.kinds.Events')

        when:
        def bean = context.getBean(context.classLoader.loadClass('ordering.kinds.MyBean'))
        bean.work()
        context.stop()
        def log = events.LOG

        then: 'each interceptor is selected for its own kind and no other'
        log.findAll { it.endsWith('@AROUND_CONSTRUCT') } == ['construct@AROUND_CONSTRUCT']
        log.findAll { it.endsWith('@POST_CONSTRUCT') } == ['post@POST_CONSTRUCT']
        log.findAll { it.endsWith('@AROUND') } == ['around@AROUND']
        log.findAll { it.endsWith('@PRE_DESTROY') } == ['pre@PRE_DESTROY']

        cleanup:
        context.close()
    }

    void 'test interceptors do not leak between beans with different bindings'() {
        given:
        ApplicationContext context = buildContext('''
package ordering.isolation;

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
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Red {
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Around
@AroundConstruct
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Blue {
}

class Events {
    static final List<String> LOG = new ArrayList<>();
}

@Prototype
@InterceptorBinding(value = Red.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Red.class, kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(value = Red.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Red.class, kind = InterceptorKind.PRE_DESTROY)
class RedInterceptor implements Interceptor<Object, Object> {
    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        Events.LOG.add("red:" + (context instanceof ConstructorInvocationContext
            ? ((ConstructorInvocationContext<?>) context).getDeclaringType().getSimpleName()
            : context.getTarget().getClass().getSimpleName()));
        return context.proceed();
    }
}

@Prototype
@InterceptorBinding(value = Blue.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Blue.class, kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(value = Blue.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Blue.class, kind = InterceptorKind.PRE_DESTROY)
class BlueInterceptor implements Interceptor<Object, Object> {
    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        Events.LOG.add("blue:" + (context instanceof ConstructorInvocationContext
            ? ((ConstructorInvocationContext<?>) context).getDeclaringType().getSimpleName()
            : context.getTarget().getClass().getSimpleName()));
        return context.proceed();
    }
}

@Singleton
@Red
class RedBean {
    @PostConstruct void init() {}
    @PreDestroy void close() {}
}

@Singleton
@Blue
class BlueBean {
    @PostConstruct void init() {}
    @PreDestroy void close() {}
}
''')
        Class<?> events = context.classLoader.loadClass('ordering.isolation.Events')

        when:
        context.getBean(context.classLoader.loadClass('ordering.isolation.RedBean'))
        context.getBean(context.classLoader.loadClass('ordering.isolation.BlueBean'))
        context.stop()
        def log = events.LOG

        then: 'neither interceptor ever sees the other binding of bean'
        !log.isEmpty()
        log.findAll { it.startsWith('red:') }.every { it.contains('RedBean') }
        log.findAll { it.startsWith('blue:') }.every { it.contains('BlueBean') }

        cleanup:
        context.close()
    }

    void 'test a constructor interceptor is not selected for lifecycle phases'() {
        given:
        ApplicationContext context = buildContext('''
package ordering.ctor;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@AroundConstruct
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Managed {
}

class Events {
    static final List<String> LOG = new ArrayList<>();
}

@Prototype
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.PRE_DESTROY)
class CtorInterceptor implements ConstructorInterceptor<Object> {
    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        Events.LOG.add("ctor");
        return context.proceed();
    }
}

@Prototype
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.PRE_DESTROY)
class LifecycleInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Events.LOG.add("method:" + context.getKind().name());
        return context.proceed();
    }
}

@Singleton
@Managed
class MyBean {
    @PostConstruct void init() {}
    @PreDestroy void close() {}
}
''')
        Class<?> events = context.classLoader.loadClass('ordering.ctor.Events')

        when:
        context.getBean(context.classLoader.loadClass('ordering.ctor.MyBean'))
        context.stop()
        def log = events.LOG

        then: 'the constructor interceptor runs once, the method interceptor only for the lifecycle kinds'
        log.count { it == 'ctor' } == 1
        log.findAll { it.startsWith('method:') } == ['method:POST_CONSTRUCT', 'method:PRE_DESTROY']

        cleanup:
        context.close()
    }

    void 'test lifecycle advice on a proxyTarget bean'() {
        given:
        ApplicationContext context = buildContext('''
package ordering.proxytarget;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Around(proxyTarget = true)
@AroundConstruct
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {
}

class Events {
    static final List<String> LOG = new ArrayList<>();
    static int instances;
}

@Prototype
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.PRE_DESTROY)
class TrackingInterceptor implements Interceptor<Object, Object> {
    private final int id = ++Events.instances;

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        String kind = context instanceof ConstructorInvocationContext
            ? "AROUND_CONSTRUCT"
            : ((MethodInvocationContext<?, ?>) context).getKind().name();
        Events.LOG.add(id + ":" + kind);
        return context.proceed();
    }
}

@Singleton
@Tracked
class MyBean {
    @PostConstruct void init() {}
    String work() { return "done"; }
    @PreDestroy void close() {}
}
''')
        Class<?> events = context.classLoader.loadClass('ordering.proxytarget.Events')

        when:
        def bean = context.getBean(context.classLoader.loadClass('ordering.proxytarget.MyBean'))
        bean.work()
        context.stop()
        def log = events.LOG

        then: 'each phase is intercepted exactly once and in order'
        log.collect { it.substring(it.indexOf(':') + 1) } == ['AROUND_CONSTRUCT', 'POST_CONSTRUCT', 'AROUND', 'PRE_DESTROY']

        and: 'construction, post construct and pre destroy of the target share one interceptor'
        log.findAll { !it.endsWith(':AROUND') }.collect { it.substring(0, it.indexOf(':')) }.toSet().size() == 1

        cleanup:
        context.close()
    }

    void 'test lifecycle advice on a factory produced bean'() {
        given:
        ApplicationContext context = buildContext('''
package ordering.factory;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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

class Events {
    static final List<String> LOG = new ArrayList<>();
    static int instances;
}

@Prototype
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.PRE_DESTROY)
class TrackingInterceptor implements Interceptor<Object, Object> {
    private final int id = ++Events.instances;

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        Events.LOG.add(id + ":" + ((MethodInvocationContext<?, ?>) context).getKind().name());
        return context.proceed();
    }
}

class MyBean {
    @PostConstruct void init() {}
    public String work() { return "done"; }
    @PreDestroy void close() {}
}

@Factory
class MyFactory {
    @Singleton
    @Tracked
    MyBean myBean() {
        return new MyBean();
    }
}
''')
        Class<?> events = context.classLoader.loadClass('ordering.factory.Events')

        when:
        def bean = context.getBean(context.classLoader.loadClass('ordering.factory.MyBean'))
        bean.work()
        context.stop()
        def log = events.LOG

        then: 'every phase of a factory produced bean is intercepted, in order'
        log.collect { it.substring(it.indexOf(':') + 1) } == ['POST_CONSTRUCT', 'AROUND', 'PRE_DESTROY']

        and: 'the lifecycle phases share one interceptor'
        log.findAll { !it.endsWith(':AROUND') }.collect { it.substring(0, it.indexOf(':')) }.toSet().size() == 1

        cleanup:
        context.close()
    }
}
