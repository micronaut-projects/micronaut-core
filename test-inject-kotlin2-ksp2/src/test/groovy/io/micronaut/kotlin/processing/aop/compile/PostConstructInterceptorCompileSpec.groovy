package io.micronaut.kotlin.processing.aop.compile

import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext
import spock.lang.PendingFeature
import spock.lang.Specification
import spock.lang.Unroll
import static io.micronaut.annotation.processing.test.KotlinCompiler.*

class PostConstructInterceptorCompileSpec extends Specification {

    @Unroll
    void 'test post construct with around interception - proxyTarget = #proxyTarget'() {
        given:
        ApplicationContext context = buildContext("""
package annbinding1

import io.micronaut.aop.*
import jakarta.inject.*
import jakarta.annotation.PostConstruct

@Singleton
@TestAnn
open class MyBean(env: io.micronaut.context.env.Environment) {

    @Inject lateinit var env: io.micronaut.context.env.Environment

    var invoked = 0

    open fun test() {
    }

    @PostConstruct
    fun init() {
        println("INVOKED POST CONSTRUCT")
        invoked++
    }
}

@io.micronaut.context.annotation.Factory
class MyFactory {

    @TestAnn
    @Singleton
    fun test(env: io.micronaut.context.env.Environment): MyOtherBean {
        return MyOtherBean()
    }
}

open class MyOtherBean

@Retention
@Target(AnnotationTarget.FUNCTION,  AnnotationTarget.CLASS)
@Around(proxyTarget=$proxyTarget)
@InterceptorBinding(kind=InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind=InterceptorKind.PRE_DESTROY)
annotation class TestAnn


@Singleton
@InterceptorBean(TestAnn::class)
class TestInterceptor: MethodInterceptor<Any, Any> {
    var invoked = 0

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        invoked++
        return context.proceed()
    }
}

@Singleton
@InterceptorBinding(value=TestAnn::class, kind=InterceptorKind.POST_CONSTRUCT)
class PostConstructTestInterceptor: MethodInterceptor<Any, Any> {
    var invoked = 0

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        invoked++
        return context.proceed()
    }
}

@Singleton
@InterceptorBinding(value=TestAnn::class, kind=InterceptorKind.PRE_DESTROY)
class PreDestroyTestInterceptor: MethodInterceptor<Any, Any> {
    var invoked = 0

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        invoked++
        return context.proceed()
    }
}

@Singleton
class AnotherInterceptor: Interceptor<Any, Any> {
    var invoked = 0
    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked++
        return context.proceed()
    }
}
""")
        when:
        def interceptor = getBean(context, 'annbinding1.TestInterceptor')
        def constructorInterceptor = getBean(context, 'annbinding1.PostConstructTestInterceptor')
        def destroyInterceptor = getBean(context, 'annbinding1.PreDestroyTestInterceptor')
        def anotherInterceptor = getBean(context, 'annbinding1.AnotherInterceptor')

        then:
        !interceptor.invoked
        !anotherInterceptor.invoked
        !constructorInterceptor.invoked

        when:"A bean that featuring post construct injection is instantiated"
        def instance = getBean(context, 'annbinding1.MyBean')

        then:"The interceptors that apply to post construction are invoked"
        (proxyTarget ? instance.interceptedTarget() : instance).invoked == 1
        interceptor.invoked == 1
        constructorInterceptor.invoked == 1
        anotherInterceptor.invoked == 0
        destroyInterceptor.invoked == 0


        when:"A method with interception is invoked"
        instance.test()

        then:"the methods interceptor are invoked"
        instance instanceof Intercepted
        interceptor.invoked == 2
        constructorInterceptor.invoked == 1
        anotherInterceptor.invoked == 0


        when:"A bean that is created from a factory is instantiated"
        def factoryCreatedInstance = getBean(context, 'annbinding1.MyOtherBean')

        then:"post construct interceptors are invoked for the created instance"
        interceptor.invoked == 3
        constructorInterceptor.invoked == 2
        anotherInterceptor.invoked == 0

        when:
        context.stop()

        then:
        // TODO: Discuss why we are invoking destroy hooks for proxies
        interceptor.invoked == proxyTarget ? 6 : 5
        constructorInterceptor.invoked == 2
        anotherInterceptor.invoked == 0
        // TODO: Discuss why we are invoking destroy hooks for proxies
        destroyInterceptor.invoked == proxyTarget ? 3 : 2


        where:
        proxyTarget << [true, false]
    }

    void 'test post construct & pre destroy without around interception'() {
        given:
        ApplicationContext context = buildContext("""
package annbinding1

import io.micronaut.aop.*
import jakarta.inject.*
import jakarta.annotation.PostConstruct

@Singleton
@TestAnn
open class MyBean(env: io.micronaut.context.env.Environment) {

    @Inject lateinit var env: io.micronaut.context.env.Environment

    var invoked = 0

    open fun test() {
    }

    @PostConstruct
    fun init() {
        println("INVOKED POST CONSTRUCT")
        invoked++
    }
}

@io.micronaut.context.annotation.Factory
class MyFactory {

    @TestAnn
    @Singleton
    fun test(env: io.micronaut.context.env.Environment): MyOtherBean {
        return MyOtherBean()
    }
}

class MyOtherBean

@Retention
@Target(AnnotationTarget.FUNCTION,  AnnotationTarget.CLASS)
@InterceptorBinding(kind=InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind=InterceptorKind.PRE_DESTROY)
annotation class TestAnn

@Singleton
@InterceptorBean(TestAnn::class)
class TestInterceptor: MethodInterceptor<Any, Any> {
    var invoked = 0

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        invoked++
        return context.proceed()
    }
}

@Singleton
@InterceptorBinding(value=TestAnn::class, kind=InterceptorKind.POST_CONSTRUCT)
class PostConstructTestInterceptor: MethodInterceptor<Any, Any> {
    var invoked = 0

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        invoked++
        return context.proceed()
    }
}

@Singleton
@InterceptorBinding(value=TestAnn::class, kind=InterceptorKind.PRE_DESTROY)
class PreDestroyTestInterceptor: MethodInterceptor<Any, Any> {
    var invoked = 0

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        invoked++
        return context.proceed()
    }
}

@Singleton
class AnotherInterceptor: Interceptor<Any, Any> {
    var invoked = 0
    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked++
        return context.proceed()
    }
}
""")
        when:
        def interceptor = getBean(context, 'annbinding1.TestInterceptor')
        def constructorInterceptor = getBean(context, 'annbinding1.PostConstructTestInterceptor')
        def destroyInterceptor = getBean(context, 'annbinding1.PreDestroyTestInterceptor')
        def anotherInterceptor = getBean(context, 'annbinding1.AnotherInterceptor')

        then:
        !interceptor.invoked
        !anotherInterceptor.invoked
        !constructorInterceptor.invoked

        when:"A bean that featuring post construct injection is instantiated"
        def instance = getBean(context, 'annbinding1.MyBean')

        then:"The interceptors that apply to post construction are invoked"
        interceptor.invoked == 1
        instance.invoked == 1
        constructorInterceptor.invoked == 1
        anotherInterceptor.invoked == 0
        destroyInterceptor.invoked == 0


        when:"A method with interception is invoked"
        instance.test()

        then:"the methods interceptor are invoked"
        interceptor.invoked == 1
        constructorInterceptor.invoked == 1
        anotherInterceptor.invoked == 0


        when:"A bean that is created from a factory is instantiated"
        def factoryCreatedInstance = getBean(context, 'annbinding1.MyOtherBean')

        then:"post construct interceptors are invoked for the created instance"
        interceptor.invoked == 2
        constructorInterceptor.invoked == 2
        anotherInterceptor.invoked == 0

        when:
        context.stop()

        then:
        interceptor.invoked == 4
        constructorInterceptor.invoked == 2
        anotherInterceptor.invoked == 0
        destroyInterceptor.invoked == 2
    }
}
