package io.micronaut.kotlin.processing.inject.privatector

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec

class PrivateInjectConstructorSpec extends AbstractKotlinCompilerSpec {

    void "test a private @Inject constructor is invoked with reflection"() {
        given:
        def context = buildContext('''
package privatector.plain

import io.micronaut.context.annotation.Prototype
import io.micronaut.core.annotation.ReflectiveAccess
import jakarta.inject.Inject

@Prototype
class Service @Inject @ReflectiveAccess private constructor()
''')

        expect:
        getBean(context, 'privatector.plain.Service') != null

        cleanup:
        context.close()
    }

    void "test an unscoped bean with a private @Inject constructor is discovered"() {
        given:
        def context = buildContext('''
package privatector.unscoped

import io.micronaut.core.annotation.ReflectiveAccess
import jakarta.inject.Inject

class Service @Inject @ReflectiveAccess private constructor()
''')

        expect:
        getBean(context, 'privatector.unscoped.Service') != null

        cleanup:
        context.close()
    }

    void "test a private @Inject constructor with arguments and #annotations"() {
        given:
        def context = buildContext("""
package privatector.args

import io.micronaut.context.annotation.Prototype
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Prototype
class Service {
    val dependency: Dependency?
    val origin: String

    $annotations
    private constructor(dependency: Dependency) {
        this.dependency = dependency
        this.origin = "injected"
    }

    constructor() {
        this.dependency = null
        this.origin = "public"
    }
}

@Singleton
class Dependency
""")

        when:
        def service = getBean(context, 'privatector.args.Service')

        then:
        service.origin == expectedOrigin
        if (expectedOrigin == 'injected') {
            service.dependency.is(getBean(context, 'privatector.args.Dependency'))
        }

        cleanup:
        context.close()

        where:
        annotations << [
            '@Inject @io.micronaut.core.annotation.ReflectiveAccess',
            '@io.micronaut.core.annotation.Creator @io.micronaut.core.annotation.ReflectiveAccess'
        ]
        expectedOrigin = 'injected'
    }

    void "test the only constructor of a bean is private"() {
        given:
        def context = buildContext('''
package privatector.sole

import io.micronaut.core.annotation.ReflectiveAccess
import jakarta.inject.Singleton

@Singleton
class Service @ReflectiveAccess private constructor()
''')

        expect:
        getBean(context, 'privatector.sole.Service') != null

        cleanup:
        context.close()
    }

    void "test a private @Inject constructor with around construct advice"() {
        given:
        def context = buildContext('''
package privatector.aroundconstruct

import io.micronaut.aop.*
import io.micronaut.context.annotation.Prototype
import io.micronaut.core.annotation.ReflectiveAccess
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Prototype
@Constructed
class Service @Inject @ReflectiveAccess private constructor()

@Retention
@Target(AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR)
@InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT)
annotation class Constructed

@Singleton
@InterceptorBean(Constructed::class)
class ConstructedInterceptor : ConstructorInterceptor<Any> {
    var invocations = 0

    override fun intercept(context: ConstructorInvocationContext<Any>): Any {
        invocations++
        return context.proceed()
    }
}
''')

        when:
        def service = getBean(context, 'privatector.aroundconstruct.Service')

        then:
        service != null
        getBean(context, 'privatector.aroundconstruct.ConstructedInterceptor').invocations == 1

        cleanup:
        context.close()
    }

    void "test a private @Inject constructor without @ReflectiveAccess fails compilation"() {
        when:
        buildBeanDefinition('privatector.noreflectiveaccess.Service', '''
package privatector.noreflectiveaccess

import io.micronaut.context.annotation.Prototype
import jakarta.inject.Inject

@Prototype
class Service @Inject private constructor()
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Constructor is declared private and is not accessible for the instantiation. To instantiate the bean using reflection annotate the constructor with @ReflectiveAccess')
    }

    void "test around advice on a bean with a private @Inject constructor fails compilation"() {
        when:
        buildBeanDefinition('privatector.around.Service', '''
package privatector.around

import io.micronaut.aop.*
import io.micronaut.context.annotation.Prototype
import io.micronaut.core.annotation.ReflectiveAccess
import jakarta.inject.Inject

@Prototype
open class Service @Inject @ReflectiveAccess private constructor() {

    @Intercepting
    open fun hello() = "hello"
}

@Retention
@Target(AnnotationTarget.FUNCTION)
@Around
annotation class Intercepting
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Cannot apply AOP advice to a bean created with a private constructor. The constructor must be made non-private to support proxying: privatector.around.Service')
    }
}
