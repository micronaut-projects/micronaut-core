package io.micronaut.aop.compile

import io.micronaut.aop.Intercepted
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import spock.lang.Unroll

/**
 * Constructor interception wraps only the constructor call: member injection and {@code @PostConstruct} of the
 * target run after every construction interceptor has completed.
 */
class AroundConstructInjectionOrderSpec extends AbstractBeanDefinitionSpec {

    @Unroll
    void 'test injection and post-construct run after the construction interceptors of #description'() {
        given:
        ApplicationContext context = buildContext(source(pkg, binding, ''))

        when:
        def target = getBean(context, pkg + '.Service')

        then:
        events(context, pkg) == [
                'A before proceed',
                'B before proceed',
                'target constructor',
                'B after proceed',
                'A after proceed',
                'target setter injection, field injected: true',
                'target postConstruct'
        ]
        (target instanceof Intercepted) == proxied
        target.field != null

        cleanup:
        context.close()

        where:
        description              | pkg                    | binding   | proxied
        'a bean without a proxy' | 'ctordergroovy.plain'  | ''        | false
        'an around proxy'        | 'ctordergroovy.proxy'  | '@Around' | true
    }

    void 'test an interceptor that throws after proceed prevents injection and post-construct'() {
        given:
        ApplicationContext context = buildContext(source('ctordergroovy.rejected', '', '''
        if (id() == "A") {
            throw new IllegalStateException("rejected by A")
        }
'''))

        when:
        getBean(context, 'ctordergroovy.rejected.Service')

        then:
        IllegalStateException e = thrown()
        e.message == 'rejected by A'
        events(context, 'ctordergroovy.rejected') == [
                'A before proceed',
                'B before proceed',
                'target constructor',
                'B after proceed'
        ]

        cleanup:
        context.close()
    }

    private static List<String> events(ApplicationContext context, String pkg) {
        return new ArrayList<>(context.classLoader.loadClass(pkg + '.Events').LOG as List<String>)
    }

    private static String source(String pkg, String binding, String afterProceed) {
        return """
package $pkg

import io.micronaut.aop.*
import io.micronaut.context.annotation.Prototype
import io.micronaut.core.order.Ordered
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.lang.annotation.*

class Events {
    static final List<String> LOG = []
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE])
$binding
@AroundConstruct
@interface Staged {
}

@Singleton
class Dependency {
}

abstract class Recording implements ConstructorInterceptor<Object>, Ordered {
    abstract String id()

    @Override
    Object intercept(ConstructorInvocationContext<Object> context) {
        Events.LOG.add(id() + " before proceed")
        Object bean = context.proceed()
        $afterProceed
        Events.LOG.add(id() + " after proceed")
        return bean
    }
}

@Singleton
@InterceptorBean(Staged)
class A extends Recording {
    String id() { "A" }
    int getOrder() { 1 }
}

@Singleton
@InterceptorBean(Staged)
class B extends Recording {
    String id() { "B" }
    int getOrder() { 2 }
}

@Prototype
@Staged
class Service {
    @Inject
    public Dependency field

    Service(Dependency constructorDependency) {
        Events.LOG.add("target constructor")
    }

    @Inject
    void setDependency(Dependency dependency) {
        Events.LOG.add("target setter injection, field injected: " + (field != null))
    }

    @PostConstruct
    void init() {
        Events.LOG.add("target postConstruct")
    }

    Dependency getField() {
        return field
    }
}
"""
    }
}
