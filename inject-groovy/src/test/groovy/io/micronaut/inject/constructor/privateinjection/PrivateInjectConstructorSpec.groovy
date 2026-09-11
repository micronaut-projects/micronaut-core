package io.micronaut.inject.constructor.privateinjection

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec

class PrivateInjectConstructorSpec extends AbstractBeanDefinitionSpec {

    void "test a private @Inject constructor is invoked with reflection"() {
        given:
        def context = buildContext('''
package privatector.plain

import io.micronaut.context.annotation.Prototype
import jakarta.inject.Inject

@Prototype
class Service {
    @Inject
    private Service() {
    }
}
''')

        expect:
        getBean(context, 'privatector.plain.Service') != null

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
    final Dependency dependency
    final String origin

    $annotations
    private Service(Dependency dependency) {
        this.dependency = dependency
        this.origin = "injected"
    }

    Service() {
        this.dependency = null
        this.origin = "public"
    }
}

@Singleton
class Dependency {
}
""")

        when:
        def service = getBean(context, 'privatector.args.Service')

        then:
        service.origin == 'injected'
        service.dependency.is(getBean(context, 'privatector.args.Dependency'))

        cleanup:
        context.close()

        where:
        annotations << [
            '@Inject',
            '@Inject @io.micronaut.core.annotation.ReflectiveAccess',
            '@io.micronaut.core.annotation.Creator'
        ]
    }

    void "test the only constructor of a bean is private"() {
        given:
        def context = buildContext('''
package privatector.sole

import jakarta.inject.Singleton

@Singleton
class Service {
    private Service() {
    }
}
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
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.lang.annotation.*

@Prototype
@Constructed
class Service {
    @Inject
    private Service() {
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE, ElementType.CONSTRUCTOR])
@InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT)
@interface Constructed {
}

@Singleton
@InterceptorBean(Constructed)
class ConstructedInterceptor implements ConstructorInterceptor<Object> {
    int invocations

    @Override
    Object intercept(ConstructorInvocationContext<Object> context) {
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

    void "test around advice on a bean with a private @Inject constructor fails compilation"() {
        when:
        buildBeanDefinition('privatector.around.Service', '''
package privatector.around

import io.micronaut.aop.*
import io.micronaut.context.annotation.Prototype
import jakarta.inject.Inject
import java.lang.annotation.*

@Prototype
class Service {
    @Inject
    private Service() {
    }

    @Intercepting
    String hello() {
        return "hello"
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Around
@interface Intercepting {
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Cannot apply AOP advice to a bean created with a private constructor. The constructor must be made non-private to support proxying: privatector.around.Service')
    }
}
