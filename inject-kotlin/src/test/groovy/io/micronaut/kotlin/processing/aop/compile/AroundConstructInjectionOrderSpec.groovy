package io.micronaut.kotlin.processing.aop.compile

import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.annotation.processing.test.KotlinCompiler.buildContext
import static io.micronaut.annotation.processing.test.KotlinCompiler.getBean

/**
 * Constructor interception wraps only the constructor call: member injection and {@code @PostConstruct} of the
 * target run after every construction interceptor has completed.
 */
class AroundConstructInjectionOrderSpec extends Specification {

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

        cleanup:
        context.close()

        where:
        description              | pkg                    | binding   | proxied
        'a bean without a proxy' | 'ctororderkt.plain'    | ''        | false
        'an around proxy'        | 'ctororderkt.proxy'    | '@Around' | true
    }

    void 'test an interceptor that throws after proceed prevents injection and post-construct'() {
        given:
        ApplicationContext context = buildContext(source('ctororderkt.rejected', '', '''
        if (id() == "A") {
            throw IllegalStateException("rejected by A")
        }
'''))

        when:
        getBean(context, 'ctororderkt.rejected.Service')

        then:
        IllegalStateException e = thrown()
        e.message == 'rejected by A'
        events(context, 'ctororderkt.rejected') == [
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

object Events {
    @JvmField
    val LOG = mutableListOf<String>()
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
$binding
@AroundConstruct
annotation class Staged

@Singleton
class Dependency

abstract class Recording : ConstructorInterceptor<Any>, Ordered {
    abstract fun id(): String

    override fun intercept(context: ConstructorInvocationContext<Any>): Any {
        Events.LOG.add(id() + " before proceed")
        val bean = context.proceed()
        $afterProceed
        Events.LOG.add(id() + " after proceed")
        return bean
    }
}

@Singleton
@InterceptorBean(Staged::class)
class A : Recording() {
    override fun id() = "A"
    override fun getOrder() = 1
}

@Singleton
@InterceptorBean(Staged::class)
class B : Recording() {
    override fun id() = "B"
    override fun getOrder() = 2
}

@Prototype
@Staged
open class Service(constructorDependency: Dependency) {
    @Inject
    lateinit var field: Dependency

    init {
        Events.LOG.add("target constructor")
    }

    @Inject
    open fun setDependency(dependency: Dependency) {
        Events.LOG.add("target setter injection, field injected: " + this::field.isInitialized)
    }

    @PostConstruct
    open fun init() {
        Events.LOG.add("target postConstruct")
    }
}
"""
    }
}
