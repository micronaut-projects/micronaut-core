package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * The dependents of a factory-produced bean have to survive its creation.
 *
 * <p>A generated bean definition records the bean that produced it by looking the factory up and then telling the
 * resolution context that the registration just added is the factory, so that a {@code @Prototype} factory is
 * destroyed as soon as the bean it produced has been built. For a bean with constructor advice that lookup happens
 * in {@code doInstantiate}, which runs after the factory method arguments and the bean's interceptors have both been
 * recorded in the same dependent list, and for a {@code @Singleton} factory the lookup records nothing at all.
 * Identifying the factory by its position in that list therefore picks whatever was recorded first - an interceptor,
 * or a dependency of the factory method - and destroys it as soon as the bean is created.</p>
 */
class FactoryProducedBeanDependentsSpec extends AbstractTypeElementSpec {

    /**
     * A snapshot, because Spock renders a failed condition lazily and the list keeps being appended to by the
     * cleanup that closes the context.
     */
    private static List<String> recorded(Class<?> callsType) {
        new ArrayList<String>(callsType.RECORDED)
    }

    private static String source(String pkg, String producedScope) {
        """
package $pkg;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

class Calls {
    static final List<String> RECORDED = new ArrayList<>();
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@AroundConstruct
@interface Managed {
}

@Prototype
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.AROUND_CONSTRUCT)
class ManagedInterceptor implements ConstructorInterceptor<Object> {
    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        Calls.RECORDED.add("construct");
        return context.proceed();
    }

    @PreDestroy
    public void close() {
        Calls.RECORDED.add("interceptor-destroyed");
    }
}

@Prototype
class MethodArgument {

    @PreDestroy
    public void close() {
        Calls.RECORDED.add("argument-destroyed");
    }
}

class Produced {
    public String work() {
        return "done";
    }

    public void close() {
        Calls.RECORDED.add("bean-destroyed");
    }
}

@Factory
class ProducedFactory {

    @$producedScope
    @Managed
    @Bean(preDestroy = "close")
    public Produced produced(MethodArgument argument) {
        return new Produced();
    }
}
"""
    }

    void 'test the dependents of a singleton produced by a singleton factory are not destroyed at creation'() {
        given:
        ApplicationContext context = buildContext(source('factorydependents.singleton', 'Singleton'))
        Class<?> callsType = context.classLoader.loadClass('factorydependents.singleton.Calls')

        when:
        def bean = context.getBean(context.classLoader.loadClass('factorydependents.singleton.Produced'))

        then: 'creating the bean destroys nothing'
        bean.work() == 'done'
        recorded(callsType) == ['construct']

        when:
        callsType.RECORDED.clear()
        context.stop()

        then: 'the dependents are destroyed with the bean they were created for, after the bean itself'
        recorded(callsType) == ['bean-destroyed', 'interceptor-destroyed', 'argument-destroyed']

        cleanup:
        context.close()
    }

    void 'test one prototype interceptor serves every phase of a factory produced bean and is destroyed with it'() {
        given: 'advice covering construction, post construct and pre destroy, which is what SharedInterceptorRegistrations carries the resolved interceptors through'
        ApplicationContext context = buildContext('''
package factorydependents.shared;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@AroundConstruct
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {
}

@Prototype
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
    public void destroy() {
        events.add(id + ":DESTROYED");
    }
}

class Produced {
    public void close() {
    }
}

@Factory
class ProducedFactory {

    @Singleton
    @Tracked
    @Bean(preDestroy = "close")
    public Produced produced() {
        return new Produced();
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('factorydependents.shared.TrackingInterceptor')

        when:
        context.getBean(context.classLoader.loadClass('factorydependents.shared.Produced'))

        then: 'the one interceptor resolved for the bean serves construction and post construct, and survives them'
        interceptorType.instances == 1
        new ArrayList<String>(interceptorType.events) == ['1:AROUND_CONSTRUCT', '1:POST_CONSTRUCT']

        when:
        context.stop()

        then: 'the same instance serves pre destroy and is only then destroyed, as a dependent of the bean'
        interceptorType.instances == 1
        new ArrayList<String>(interceptorType.events) == [
                '1:AROUND_CONSTRUCT', '1:POST_CONSTRUCT', '1:PRE_DESTROY', '1:DESTROYED'
        ]

        cleanup:
        context.close()
    }

    void 'test the dependents of a prototype produced by a singleton factory are not destroyed at creation'() {
        given:
        ApplicationContext context = buildContext(source('factorydependents.prototype', 'Prototype'))
        Class<?> callsType = context.classLoader.loadClass('factorydependents.prototype.Calls')

        when:
        def bean = context.getBean(context.classLoader.loadClass('factorydependents.prototype.Produced'))

        then: 'creating the bean destroys nothing'
        bean.work() == 'done'
        recorded(callsType) == ['construct']

        cleanup:
        context.close()
    }
}
