package io.micronaut.kotlin.processing.aop.compile

import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import spock.lang.PendingFeature
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.annotation.processing.test.KotlinCompiler.*

class AroundConstructCompileSpec extends Specification {

    void 'test around construct with annotation mapper - plus members'() {
        given:
        ApplicationContext context = buildContext('''
package aroundconstructmapperbindingmembers

import io.micronaut.aop.*
import jakarta.inject.Singleton

@Singleton
@TestAnn2
class MyBean @TestAnn(num=1) constructor() {

}

@Retention
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.ANNOTATION_CLASS)
annotation class MyInterceptorBinding

@Retention
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.CLASS)
@MyInterceptorBinding
annotation class TestAnn(val num: Int)

@Retention
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.CLASS)
@MyInterceptorBinding
annotation class TestAnn2

@Singleton
@TestAnn(num=1)
class TestInterceptor: ConstructorInterceptor<Any> {
    var invoked = false

    override fun intercept(context: ConstructorInvocationContext<Any>): Any {
        invoked = true
        return context.proceed()
    }
}

@Singleton
@TestAnn(num=2)
class TestInterceptor2: ConstructorInterceptor<Any> {
    var invoked = false

    override fun intercept(context: ConstructorInvocationContext<Any>): Any {
        invoked = true
        return context.proceed()
    }
}
''')


        when:
        def interceptor = getBean(context, 'aroundconstructmapperbindingmembers.TestInterceptor')
        def interceptor2 = getBean(context, 'aroundconstructmapperbindingmembers.TestInterceptor2')

        then:
        !interceptor.invoked
        !interceptor2.invoked

        when:
        def instance = getBean(context, 'aroundconstructmapperbindingmembers.MyBean')

        then:"the interceptor was invoked"
        interceptor.invoked
        !interceptor2.invoked
    }

    void 'test around construct on type and constructor with proxy target + bind members'() {
        given:
        ApplicationContext context = buildContext("""
package ctorbinding

import io.micronaut.aop.*
import jakarta.inject.Singleton

@FooClassBinding
@Singleton
open class Foo @FooCtorBinding constructor() {
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR)
@Retention
@MustBeDocumented
@InterceptorBinding(kind = InterceptorKind.AROUND, bindMembers = true)
@InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT, bindMembers = true)
annotation class FooCtorBinding

@Target(AnnotationTarget.CLASS)
@Retention
@MustBeDocumented
@InterceptorBinding(kind = InterceptorKind.AROUND, bindMembers = true)
@InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT, bindMembers = true)
@Around(proxyTarget = true)
annotation class FooClassBinding

@Singleton
@FooClassBinding
class Interceptor1: ConstructorInterceptor<Any> {
    var intercepted = false

    override fun intercept(context: ConstructorInvocationContext<Any>): Any {
        intercepted = true
        return context.proceed()
    }
}

@Singleton
@FooCtorBinding
class Interceptor2: ConstructorInterceptor<Any> {
    var intercepted = false

    override fun intercept(context: ConstructorInvocationContext<Any>): Any {
        intercepted = true
        return context.proceed()
    }
}
""")
        when:
        def i1 = getBean(context, 'ctorbinding.Interceptor1')
        def i2 = getBean(context, 'ctorbinding.Interceptor2')

        then:
        !i1.intercepted
        !i2.intercepted

        when:
        def bean = getBean(context, 'ctorbinding.Foo')

        then:
        i1.intercepted
        i2.intercepted

        cleanup:
        context.close()
    }

    void 'test around construct on type and constructor with proxy target'() {
        given:
        ApplicationContext context = buildContext("""
package ctorbinding

import io.micronaut.aop.*
import jakarta.inject.Singleton

@FooClassBinding
@Singleton
open class Foo @FooCtorBinding constructor() {
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR)
@Retention
@MustBeDocumented
@InterceptorBinding(kind = InterceptorKind.AROUND)
@InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT)
annotation class FooCtorBinding

@Target(AnnotationTarget.CLASS)
@Retention
@MustBeDocumented
@InterceptorBinding(kind = InterceptorKind.AROUND)
@InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT)
@Around(proxyTarget = true)
annotation class FooClassBinding

@Singleton
@FooClassBinding
class Interceptor1: ConstructorInterceptor<Any> {
    var intercepted = false

    override fun intercept(context: ConstructorInvocationContext<Any>): Any {
        intercepted = true
        return context.proceed()
    }
}

@Singleton
@FooCtorBinding
class Interceptor2: ConstructorInterceptor<Any> {
    var intercepted = false

    override fun intercept(context: ConstructorInvocationContext<Any>): Any {
        intercepted = true
        return context.proceed()
    }
}
""")
        when:
        def i1 = getBean(context, 'ctorbinding.Interceptor1')
        def i2 = getBean(context, 'ctorbinding.Interceptor2')

        then:
        !i1.intercepted
        !i2.intercepted

        when:
        def bean = getBean(context, 'ctorbinding.Foo')

        then:
        i1.intercepted
        i2.intercepted

        cleanup:
        context.close()
    }

    void 'test around construct on type and constructor'() {
        given:
        ApplicationContext context = buildContext("""
package ctorbinding

import io.micronaut.aop.*
import jakarta.inject.Singleton

@FooClassBinding
@Singleton
open class Foo @FooCtorBinding constructor() {
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR)
@Retention
@MustBeDocumented
@InterceptorBinding(kind = InterceptorKind.AROUND)
@InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT)
annotation class FooCtorBinding

@Target(AnnotationTarget.CLASS)
@Retention
@MustBeDocumented
@InterceptorBinding(kind = InterceptorKind.AROUND)
@InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT)
annotation class FooClassBinding

@Singleton
@FooClassBinding
class Interceptor1: ConstructorInterceptor<Any> {
    var intercepted = false

    override fun intercept(context: ConstructorInvocationContext<Any>): Any {
        intercepted = true
        return context.proceed()
    }
}

@Singleton
@FooCtorBinding
class Interceptor2: ConstructorInterceptor<Any> {
    var intercepted = false

    override fun intercept(context: ConstructorInvocationContext<Any>): Any {
        intercepted = true
        return context.proceed()
    }
}
""")
        when:
        def i1 = getBean(context, 'ctorbinding.Interceptor1')
        def i2 = getBean(context, 'ctorbinding.Interceptor2')

        then:
        !i1.intercepted
        !i2.intercepted

        when:
        def bean = getBean(context, 'ctorbinding.Foo')

        then:
        i1.intercepted
        i2.intercepted

        cleanup:
        context.close()
    }

    @Unroll
    void 'test around construct with around interception - proxyTarget = #proxyTarget'() {
        given:
        ApplicationContext context = buildContext("""
package annbinding1

import io.micronaut.aop.*
import jakarta.inject.Singleton

@Singleton
@TestAnn
open class MyBean(private val env: io.micronaut.context.env.Environment) {

    open fun test() {
    }
}

@io.micronaut.context.annotation.Factory
open class MyFactory {

    @TestAnn
    @Singleton
    open fun test(env: io.micronaut.context.env.Environment): MyOtherBean {
        return MyOtherBean()
    }
}

open class MyOtherBean

@Retention
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Around(proxyTarget=$proxyTarget)
@AroundConstruct
annotation class TestAnn

@Singleton
@InterceptorBean(TestAnn::class)
class TestConstructInterceptor: ConstructorInterceptor<Any> {
    var invoked = false
    var parameters: Array<Any>? = null

    override fun intercept(context: ConstructorInvocationContext<Any>): Any {
        invoked = true
        parameters = context.parameterValues
        return context.proceed()
    }
}

@Singleton
@InterceptorBean(TestAnn::class)
class TypeSpecificConstructInterceptor: ConstructorInterceptor<MyBean> {
    var invoked = false
    var parameters: Array<Any>? = null

    override fun intercept(context: ConstructorInvocationContext<MyBean>): MyBean {
        invoked = true
        parameters = context.parameterValues
        return context.proceed()
    }
}

@Singleton
@InterceptorBinding(TestAnn::class)
class TestInterceptor: MethodInterceptor<Any, Any> {
    var invoked = false

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
}

@Singleton
class AnotherInterceptor: Interceptor<Any, Any> {
    var invoked = false

    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
}
""")
        when:
        def interceptor = getBean(context, 'annbinding1.TestInterceptor')
        def constructorInterceptor = getBean(context, 'annbinding1.TestConstructInterceptor')
        def typeSpecificInterceptor = getBean(context, 'annbinding1.TypeSpecificConstructInterceptor')
        def anotherInterceptor = getBean(context, 'annbinding1.AnotherInterceptor')

        then:
        !constructorInterceptor.invoked
        !interceptor.invoked
        !anotherInterceptor.invoked

        when:"A bean that features constructor injection is instantiated"
        def instance = getBean(context, 'annbinding1.MyBean')

        then:"The constructor interceptor is invoked"
        constructorInterceptor.invoked
        typeSpecificInterceptor.invoked
        constructorInterceptor.parameters.size() == 1

        and:"Other non-constructor interceptors are not invoked"
        !interceptor.invoked
        !anotherInterceptor.invoked

        when:"A method with interception is invoked"
        constructorInterceptor.invoked = false
        typeSpecificInterceptor.invoked = false
        instance.test()

        then:"the methods interceptor are invoked"
        instance instanceof Intercepted
        interceptor.invoked
        !anotherInterceptor.invoked

        and:"The constructor interceptor is not"
        !constructorInterceptor.invoked
        !typeSpecificInterceptor.invoked

        when:"A bean that is created from a factory is instantiated"
        constructorInterceptor.invoked = false
        interceptor.invoked = false
        def factoryCreatedInstance = getBean(context, 'annbinding1.MyOtherBean')

        then:"Constructor interceptors are invoked for the created instance"
        constructorInterceptor.invoked
        !typeSpecificInterceptor.invoked
        constructorInterceptor.parameters.size() == 1

        and:"Other interceptors are not"
        !interceptor.invoked
        !anotherInterceptor.invoked

        cleanup:
        context.close()

        where:
        proxyTarget << [true, false]
    }

    void 'test around construct without around interception'() {
        given:
        ApplicationContext context = buildContext("""
package annbinding1

import io.micronaut.aop.*
import jakarta.inject.Singleton

@Singleton
@TestAnn
open class MyBean(private val env: io.micronaut.context.env.Environment) {

    open fun test() {
    }
}

@io.micronaut.context.annotation.Factory
open class MyFactory {

    @TestAnn
    @Singleton
    open fun test(env: io.micronaut.context.env.Environment): MyOtherBean {
        return MyOtherBean()
    }
}

open class MyOtherBean

@Retention
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@AroundConstruct
annotation class TestAnn

@Singleton
@InterceptorBean(TestAnn::class)
class TestConstructInterceptor: ConstructorInterceptor<Any> {
    var invoked = false
    var parameters: Array<Any>? = null

    override fun intercept(context: ConstructorInvocationContext<Any>): Any {
        invoked = true
        parameters = context.parameterValues
        return context.proceed()
    }
}

@Singleton
@InterceptorBinding(TestAnn::class)
class TestInterceptor: MethodInterceptor<Any, Any> {
    var invoked = false

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
}

@Singleton
class AnotherInterceptor: Interceptor<Any, Any> {
    var invoked = false

    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
}
""")
        when:
        def interceptor = getBean(context, 'annbinding1.TestInterceptor')
        def constructorInterceptor = getBean(context, 'annbinding1.TestConstructInterceptor')
        def anotherInterceptor = getBean(context, 'annbinding1.AnotherInterceptor')

        then:
        !constructorInterceptor.invoked
        !interceptor.invoked
        !anotherInterceptor.invoked

        when:"A bean that features constructor injection is instantiated"
        def instance = getBean(context, 'annbinding1.MyBean')

        then:"The constructor interceptor is invoked"
        !(instance instanceof Intercepted)
        constructorInterceptor.invoked
        constructorInterceptor.parameters.size() == 1

        and:"Other non-constructor interceptors are not invoked"
        !interceptor.invoked
        !anotherInterceptor.invoked


        when:"A method with interception is invoked"
        constructorInterceptor.invoked = false
        instance.test()

        then:"the methods interceptor are invoked"
        !interceptor.invoked
        !anotherInterceptor.invoked

        and:"The constructor interceptor is not"
        !constructorInterceptor.invoked

        when:"A bean that is created from a factory is instantiated"
        constructorInterceptor.invoked = false
        interceptor.invoked = false
        def factoryCreatedInstance = getBean(context, 'annbinding1.MyOtherBean')

        then:"Constructor interceptors are invoked for the created instance"
        !(factoryCreatedInstance instanceof Intercepted)
        constructorInterceptor.invoked
        constructorInterceptor.parameters.size() == 1

        and:"Other interceptors are not"
        !interceptor.invoked
        !anotherInterceptor.invoked

        cleanup:
        context.close()
    }

    void 'test around construct declared on constructor only'() {
        given:
        ApplicationContext context = buildContext("""
package annbinding1

import io.micronaut.aop.*
import jakarta.inject.Singleton

@Singleton
class MyBean @TestAnn constructor(env: io.micronaut.context.env.Environment) {

    fun test() {
    }
}

@Retention
@Target(AnnotationTarget.CONSTRUCTOR)
@AroundConstruct
@Around
annotation class TestAnn

@Singleton
@InterceptorBean(TestAnn::class)
class TestConstructInterceptor: ConstructorInterceptor<Any> {
    var invoked = false
    var parameters: Array<Any>? = null

    override fun intercept(context: ConstructorInvocationContext<Any>): Any {
        invoked = true
        parameters = context.parameterValues
        return context.proceed()
    }
}

""")
        when:
        def constructorInterceptor = getBean(context, 'annbinding1.TestConstructInterceptor')

        then:
        !constructorInterceptor.invoked

        when:"A bean that features constructor injection is instantiated"
        def instance = getBean(context, 'annbinding1.MyBean')

        then:"The constructor interceptor is invoked"
        !(instance instanceof Intercepted)
        constructorInterceptor.invoked
        constructorInterceptor.parameters.size() == 1

        cleanup:
        context.close()
    }

    void 'test around construct without around interception - interceptors from factory'() {
        given:
        ApplicationContext context = buildContext("""
package annbinding1

import io.micronaut.aop.*
import io.micronaut.context.annotation.*
import jakarta.inject.Singleton

@Singleton
@TestAnn
class MyBean(env: io.micronaut.context.env.Environment) {

    fun test() {
    }
}

@Retention
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@AroundConstruct
annotation class TestAnn

@Factory
class InterceptorFactory {
    var aroundConstructInvoked = false

    @InterceptorBean(TestAnn::class)
    fun aroundIntercept(): ConstructorInterceptor<Any> {
        return ConstructorInterceptor { context ->
            this.aroundConstructInvoked = true
            context.proceed()
        }
    }
}
""")
        when:
        def factory = getBean(context, 'annbinding1.InterceptorFactory')

        then:
        !factory.aroundConstructInvoked

        when:"A bean that features constructor injection is instantiated"
        def instance = getBean(context, 'annbinding1.MyBean')

        then:"The constructor interceptor is invoked"
        !(instance instanceof Intercepted)
        factory.aroundConstructInvoked

        cleanup:
        context.close()
    }

    void 'test around construct with introduction advice'() {
        given:
        ApplicationContext context = buildContext("""
package annbinding1

import io.micronaut.aop.*
import jakarta.inject.Singleton

@Singleton
@TestAnn
abstract class MyBean(env: io.micronaut.context.env.Environment) {
    abstract fun test(): String
}

@Retention
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Introduction
@AroundConstruct
annotation class TestAnn

@Singleton
@InterceptorBean(TestAnn::class)
class TestConstructInterceptor: ConstructorInterceptor<Any> {
    var invoked = false
    var parameters: Array<Any>? = null

    override fun intercept(context: ConstructorInvocationContext<Any>): Any {
        invoked = true
        parameters = context.parameterValues
        return context.proceed()
    }
}

@Singleton
@InterceptorBinding(TestAnn::class)
class TestInterceptor: MethodInterceptor<Any, Any> {
    var invoked = false

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        invoked = true
        return "good"
    }
}

@Singleton
class AnotherInterceptor: Interceptor<Any, Any> {
    var invoked = false

    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
}
""")
        when:
        def interceptor = getBean(context, 'annbinding1.TestInterceptor')
        def constructorInterceptor = getBean(context, 'annbinding1.TestConstructInterceptor')
        def anotherInterceptor = getBean(context, 'annbinding1.AnotherInterceptor')

        then:
        !constructorInterceptor.invoked
        !interceptor.invoked
        !anotherInterceptor.invoked

        when:"A bean that features constructor injection is instantiated"
        def instance = getBean(context, 'annbinding1.MyBean')

        then:"The constructor interceptor is invoked"
        instance instanceof Intercepted
        constructorInterceptor.invoked
        constructorInterceptor.parameters.size() == 1
        constructorInterceptor.parameters[0] instanceof Environment

        and:"Other non-constructor interceptors are not invoked"
        !interceptor.invoked
        !anotherInterceptor.invoked

        when:"A method with interception is invoked"
        constructorInterceptor.invoked = false
        def result = instance.test()

        then:"the methods interceptor are invoked"
        interceptor.invoked
        result == 'good'
        !anotherInterceptor.invoked

        and:"The constructor interceptor is not"
        !constructorInterceptor.invoked

        cleanup:
        context.close()
    }

}

