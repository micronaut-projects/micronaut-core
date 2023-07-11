package io.micronaut.kotlin.processing.aop.compile

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.aop.Intercepted
import io.micronaut.aop.InterceptorBinding
import io.micronaut.aop.InterceptorKind
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.kotlin.processing.aop.simple.Mutating
import io.micronaut.kotlin.processing.aop.simple.TestBinding
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.AdvisedBeanType
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.writer.BeanDefinitionWriter
import spock.lang.Issue
import spock.lang.PendingFeature
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.*

class AroundCompileSpec extends Specification {

    void 'test AOP advice applied on factory with properties'() {
        when:
        def context = buildContext('''
package test

import io.micronaut.kotlin.processing.aop.simple.Mutating
import io.micronaut.kotlin.processing.aop.compile.MyClient
import io.micronaut.kotlin.processing.aop.compile.MyClientImpl
import io.micronaut.context.annotation.*
import com.fasterxml.jackson.databind.ObjectMapper

@Factory
class MyBeanFactory {

    @Mutating("someVal")
    @jakarta.inject.Singleton
    fun client(): MyClient {
        return MyClientImpl()
    }

}

''')

        def bean = context.getBean(MyClient)
        then:
        bean instanceof Intercepted
        bean.users == ["Fred", "Bob"]

        cleanup:
        context.close()
    }

    void 'test stereotype method level interceptor matching'() {
        given:
        ApplicationContext context = buildContext('''
package annbinding2

import io.micronaut.aop.Around
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.Interceptor
import io.micronaut.aop.InvocationContext
import jakarta.inject.Singleton

@Singleton
open class MyBean {
    val name : String = "test"

    @TestAnn2
    open fun test() {

    }

}

@Retention
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Around
annotation class TestAnn

@Retention
@Target(AnnotationTarget.FUNCTION)
@TestAnn
annotation class TestAnn2

@InterceptorBean(TestAnn::class)
class TestInterceptor: Interceptor<Any, Any> {
    var invoked = false

    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
}

''')
        def instance = getBean(context, 'annbinding2.MyBean')
        def interceptor = getBean(context, 'annbinding2.TestInterceptor')

        when:
        instance.test()

        then:"the interceptor was invoked"
        instance instanceof Intercepted
        interceptor.invoked

        cleanup:
        context.close()
    }

    void 'test stereotype type level interceptor matching'() {
        given:
        ApplicationContext context = buildContext('''
package annbinding2

import io.micronaut.aop.Around
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.Interceptor
import io.micronaut.aop.InvocationContext
import jakarta.inject.Singleton

@Singleton
@TestAnn2
open class MyBean {
    val name : String = "test"
    open fun test() {

    }

}

@Retention
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Around
annotation class TestAnn

@Retention
@Target(AnnotationTarget.CLASS)
@TestAnn
annotation class TestAnn2

@InterceptorBean(TestAnn::class)
class TestInterceptor: Interceptor<Any, Any> {
    var invoked = false

    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
}

''')
        def instance = getBean(context, 'annbinding2.MyBean')
        def interceptor = getBean(context, 'annbinding2.TestInterceptor')

        when:
        instance.test()

        then:"the interceptor was invoked"
        instance instanceof Intercepted
        interceptor.invoked

        cleanup:
        context.close()
    }

    void 'test apply interceptor binder with annotation mapper'() {
        given:
        ApplicationContext context = buildContext('''
package mapperbinding

import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.Interceptor
import io.micronaut.aop.InvocationContext
import jakarta.inject.Singleton

@Singleton
open class MyBean {

    @TestAnn
    open fun test() {
    }
}

@Retention
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
annotation class TestAnn


@InterceptorBean(TestAnn::class)
class TestInterceptor: Interceptor<Any, Any> {
    var invoked = false

    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
}

''')
        def instance = getBean(context,'mapperbinding.MyBean')
        def interceptor = getBean(context,'mapperbinding.TestInterceptor')

        when:
        instance.test()

        then:"the interceptor was invoked"
        instance instanceof Intercepted
        interceptor.invoked
    }

    void 'test apply interceptor binder with annotation mapper - plus members'() {
        given:
        ApplicationContext context = buildContext('''
package mapperbindingmembers

import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.Interceptor
import io.micronaut.aop.InvocationContext
import jakarta.inject.Singleton

@Singleton
open class MyBean {
    @TestAnn(num=1)
    open fun test() {
    }
}

@Retention
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class MyInterceptorBinding

@Retention
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@MyInterceptorBinding
annotation class TestAnn(val num: Int)

@Singleton
@TestAnn(num=1)
class TestInterceptor: Interceptor<Any, Any> {
    var invoked = false

    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
}

@Singleton
@TestAnn(num=2)
class TestInterceptor2: Interceptor<Any, Any> {
    var invoked = false

    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
}

''')
        def instance = getBean(context, 'mapperbindingmembers.MyBean')
        def interceptor = getBean(context, 'mapperbindingmembers.TestInterceptor')
        def interceptor2 = getBean(context, 'mapperbindingmembers.TestInterceptor2')

        when:
        instance.test()

        then:"the interceptor was invoked"
        instance instanceof Intercepted
        interceptor.invoked
        !interceptor2.invoked
    }

    void 'test method level interceptor matching'() {
        given:
        ApplicationContext context = buildContext('''
package annbinding2

import io.micronaut.aop.*
import jakarta.inject.Singleton

@Singleton
open class MyBean {

    @TestAnn
    open fun test() {

    }

    @TestAnn2
    open fun test2() {

    }
}

@Retention
@Target(AnnotationTarget.FUNCTION)
@Around
annotation class TestAnn

@Retention
@Target(AnnotationTarget.FUNCTION)
@Around
annotation class TestAnn2

@InterceptorBean(TestAnn::class)
class TestInterceptor: Interceptor<Any, Any> {
    var invoked = false

    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
}

@InterceptorBean(TestAnn2::class)
class AnotherInterceptor: Interceptor<Any, Any> {
    var invoked = false

    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
}
''')
        def instance = getBean(context, 'annbinding2.MyBean')
        def interceptor = getBean(context, 'annbinding2.TestInterceptor')
        def anotherInterceptor = getBean(context, 'annbinding2.AnotherInterceptor')

        when:
        instance.test()

        then:"the interceptor was invoked"
        instance instanceof Intercepted
        interceptor.invoked
        !anotherInterceptor.invoked

        when:
        instance.test2()

        then:
        anotherInterceptor.invoked

        cleanup:
        context.close()
    }

    void 'test annotation with just interceptor binding'() {
        given:
        ApplicationContext context = buildContext('''
package annbinding1

import io.micronaut.aop.*
import jakarta.inject.Singleton

@Singleton
@TestAnn
open class MyBean {

    open fun test() {
    }
}

@Retention
@Target(AnnotationTarget.CLASS)
@InterceptorBinding
annotation class TestAnn

@Singleton
@InterceptorBinding(TestAnn::class)
class TestInterceptor: Interceptor<Any, Any> {
    var invoked = false

    override fun intercept(context: InvocationContext<Any, Any>): Any? {
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
''')
        def instance = getBean(context, 'annbinding1.MyBean')
        def interceptor = getBean(context, 'annbinding1.TestInterceptor')
        def anotherInterceptor = getBean(context, 'annbinding1.AnotherInterceptor')
        instance.test()

        expect:"the interceptor was invoked"
        instance instanceof Intercepted
        interceptor.invoked
        !anotherInterceptor.invoked

        cleanup:
        context.close()
    }

    @PendingFeature(reason = "annotation defaults")
    void 'test multiple interceptor binding'() {
        given:
        ApplicationContext context = buildContext('''
package multiplebinding

import io.micronaut.aop.*
import io.micronaut.context.annotation.NonBinding
import jakarta.inject.Singleton

@Retention
@InterceptorBinding(kind = InterceptorKind.AROUND)
annotation class Deadly

@Retention
@InterceptorBinding(kind = InterceptorKind.AROUND)
annotation class Fast

@Retention
@InterceptorBinding(kind = InterceptorKind.AROUND)
annotation class Slow

interface Missile {
    fun fire()
}

@Fast
@Deadly
@Singleton
open class FastAndDeadlyMissile: Missile {
    override fun fire() {
    }
}

@Deadly
@Singleton
open class AnyDeadlyMissile: Missile {
    override fun fire() {
    }
}

@Singleton
open class GuidedMissile: Missile {
    @Slow
    @Deadly
    open fun lockAndFire() {
    }

    @Fast
    @Deadly
    override fun fire() {
    }
}

@Slow
@Deadly
@Singleton
open class SlowMissile: Missile {
    override fun fire() {
    }
}

@Fast
@Deadly
@Singleton
class MissileInterceptor: MethodInterceptor<Any, Any> {
    var intercepted = false

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        intercepted = true
        return context.proceed()
    }
}

@Slow
@Deadly
@Singleton
class LockInterceptor: MethodInterceptor<Any, Any> {
    var intercepted = false

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        intercepted = true
        return context.proceed()
    }
}
''')
        def missileInterceptor = getBean(context, 'multiplebinding.MissileInterceptor')
        def lockInterceptor = getBean(context, 'multiplebinding.LockInterceptor')

        when:
        missileInterceptor.intercepted = false
        lockInterceptor.intercepted = false
        def guidedMissile = getBean(context, 'multiplebinding.GuidedMissile');
        guidedMissile.fire()

        then:
        missileInterceptor.intercepted
        !lockInterceptor.intercepted

        when:
        missileInterceptor.intercepted = false
        lockInterceptor.intercepted = false
        def fastAndDeadlyMissile = getBean(context, 'multiplebinding.FastAndDeadlyMissile');
        fastAndDeadlyMissile.fire()

        then:
        missileInterceptor.intercepted
        !lockInterceptor.intercepted

        when:
        missileInterceptor.intercepted = false
        lockInterceptor.intercepted = false
        def slowMissile = getBean(context, 'multiplebinding.SlowMissile');
        slowMissile.fire()

        then:
        !missileInterceptor.intercepted
        lockInterceptor.intercepted

        when:
        missileInterceptor.intercepted = false
        lockInterceptor.intercepted = false
        def anyMissile = getBean(context, 'multiplebinding.AnyDeadlyMissile');
        anyMissile.fire()

        then:
        missileInterceptor.intercepted
        lockInterceptor.intercepted

        cleanup:
        context.close()
    }

    void 'test annotation with just interceptor binding - member binding'() {
        given:
        ApplicationContext context = buildContext('''
package memberbinding

import io.micronaut.aop.*
import io.micronaut.context.annotation.NonBinding
import jakarta.inject.Singleton

@Singleton
@TestAnn(num=1, debug = false)
open class MyBean {
    open fun test() {
    }

    @TestAnn(num=2) // overrides binding on type
    open fun test2() {

    }
}

@Retention
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@InterceptorBinding(bindMembers = true)
annotation class TestAnn(val num: Int, @get:NonBinding val debug: Boolean = false)

@InterceptorBean(TestAnn::class)
@TestAnn(num = 1, debug = true)
class TestInterceptor: Interceptor<Any, Any> {
    var invoked = false

    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
}

@InterceptorBean(TestAnn::class)
@TestAnn(num = 2)
class AnotherInterceptor: Interceptor<Any, Any> {
    var invoked = false

    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
}
''')
        def instance = getBean(context, 'memberbinding.MyBean')
        def interceptor = getBean(context, 'memberbinding.TestInterceptor')
        def anotherInterceptor = getBean(context, 'memberbinding.AnotherInterceptor')

        when:
        instance.test()

        then:"the interceptor was invoked"
        instance instanceof Intercepted
        interceptor.invoked
        !anotherInterceptor.invoked

        when:
        interceptor.invoked = false
        instance.test2()

        then:
        !interceptor.invoked
        anotherInterceptor.invoked

        cleanup:
        context.close()
    }


    void 'test annotation with just around'() {
        given:
        ApplicationContext context = buildContext('''
package justaround

import io.micronaut.aop.*
import jakarta.inject.Singleton

@Singleton
@TestAnn
open class MyBean {
    open fun test() {
    }
}

@Retention
@Target(AnnotationTarget.CLASS)
@Around
annotation class TestAnn

@InterceptorBean(TestAnn::class)
class TestInterceptor: Interceptor<Any, Any> {
    var invoked = false

    override fun intercept(context: InvocationContext<Any, Any>): Any? {
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
''')
        def instance = getBean(context, 'justaround.MyBean')
        def interceptor = getBean(context, 'justaround.TestInterceptor')
        def anotherInterceptor = getBean(context, 'justaround.AnotherInterceptor')
        instance.test()

        expect:"the interceptor was invoked"
        instance instanceof Intercepted
        interceptor.invoked
        !anotherInterceptor.invoked

        cleanup:
        context.close()
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/5522')
    void 'test Around annotation on private method fails'() {
        when:
        buildContext('''
package around.priv.method

import io.micronaut.aop.*
import jakarta.inject.Singleton

@Singleton
open class MyBean {
    @TestAnn
    private fun test() {
    }
}

@Retention
@Target(AnnotationTarget.FUNCTION)
@Around
annotation class TestAnn
''')

        then:
        Throwable t = thrown()
        t.message.contains 'Method defines AOP advice but is declared final'
    }

    void 'test byte[] return compile'() {
        given:
        ApplicationContext context = buildContext('''
package test

import io.micronaut.kotlin.processing.aop.simple.Mutating

@jakarta.inject.Singleton
@Mutating("someVal")
open class MyBean {

    open fun test(someVal: ByteArray): ByteArray? {
        return null
    }
}
''')
        def instance = getBean(context, 'test.MyBean')

        expect:
        instance != null

        cleanup:
        context.close()
    }

    void 'compile simple AOP advice'() {
        given:
        BeanDefinition beanDefinition = buildInterceptedBeanDefinition('test.MyBean', '''
package test

import io.micronaut.kotlin.processing.aop.simple.*

@jakarta.inject.Singleton
@Mutating("someVal")
@TestBinding
open class MyBean {
    open fun test() {}
}
''')

        BeanDefinitionReference ref = buildInterceptedBeanDefinitionReference('test.MyBean', '''
package test

import io.micronaut.kotlin.processing.aop.simple.*

@jakarta.inject.Singleton
@Mutating("someVal")
@TestBinding
open class MyBean {
    open fun test() {}
}
''')

        def annotationMetadata = beanDefinition?.annotationMetadata
        def values = annotationMetadata.getAnnotationValuesByType(InterceptorBinding)

        expect:
        values.size() == 2
        values[0].stringValue().get() == Mutating.name
        values[0].enumValue("kind", InterceptorKind).get() == InterceptorKind.AROUND
        values[0].classValue("interceptorType").isPresent()
        values[1].stringValue().get() == TestBinding.name
        !values[1].classValue("interceptorType").isPresent()
        values[1].enumValue("kind", InterceptorKind).get() == InterceptorKind.AROUND
        beanDefinition != null
        beanDefinition instanceof AdvisedBeanType
        beanDefinition.interceptedType.name == 'test.MyBean'
        ref in AdvisedBeanType
        ref.interceptedType.name == 'test.MyBean'
    }

    void 'test multiple annotations on a single method'() {
        given:
        ApplicationContext context = buildContext('''
package annbinding2

import io.micronaut.aop.*
import jakarta.inject.Singleton

@Singleton
open class MyBean {

    @TestAnn
    @TestAnn2
    open fun test() {
    }
}

@Retention
@Target(AnnotationTarget.FUNCTION)
@Around
annotation class TestAnn

@Retention
@Target(AnnotationTarget.FUNCTION)
@Around
annotation class TestAnn2

@InterceptorBean(TestAnn::class)
class TestInterceptor: Interceptor<Any, Any> {
    var invoked = false

    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
}

@InterceptorBean(TestAnn2::class)
class AnotherInterceptor: Interceptor<Any, Any> {
    var invoked = false

    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
}
''')
        def instance = getBean(context, 'annbinding2.MyBean')
        def interceptor = getBean(context, 'annbinding2.TestInterceptor')
        def anotherInterceptor = getBean(context, 'annbinding2.AnotherInterceptor')

        when:
        instance.test()

        then:"the interceptor was invoked"
        instance instanceof Intercepted
        interceptor.invoked
        anotherInterceptor.invoked

        cleanup:
        context.close()
    }

    void 'test multiple annotations on an interceptor and method'() {
        given:
        ApplicationContext context = buildContext('''
package annbinding2

import io.micronaut.aop.*
import jakarta.inject.Singleton

@Singleton
open class MyBean {

    @TestAnn
    @TestAnn2
    open fun test() {

    }
}

@Retention
@Target(AnnotationTarget.FUNCTION)
@Around
annotation class TestAnn

@Retention
@Target(AnnotationTarget.FUNCTION)
@Around
annotation class TestAnn2

@InterceptorBean(TestAnn::class, TestAnn2::class)
class TestInterceptor: Interceptor<Any, Any> {
    var count = 0

    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        count++
        return context.proceed()
    }
}
''')
        def instance = getBean(context, 'annbinding2.MyBean')
        def interceptor = getBean(context, 'annbinding2.TestInterceptor')

        when:
        instance.test()

        then:
        interceptor.count == 1

        cleanup:
        context.close()
    }

    void 'test multiple annotations on an interceptor'() {
        given:
        ApplicationContext context = buildContext('''
package annbinding2

import io.micronaut.aop.*
import jakarta.inject.Singleton

@Singleton
open class MyBean {

    @TestAnn
    open fun test() {
    }

    @TestAnn2
    open fun test2() {
    }

    @TestAnn
    @TestAnn2
    open fun testBoth() {
    }
}

@Retention
@Target(AnnotationTarget.FUNCTION)
@Around
annotation class TestAnn

@Retention
@Target(AnnotationTarget.FUNCTION)
@Around
annotation class TestAnn2

@InterceptorBean(TestAnn::class, TestAnn2::class)
class TestInterceptor: Interceptor<Any, Any> {
    var count = 0

    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        count++
        return context.proceed()
    }
}
''')
        def instance = getBean(context, 'annbinding2.MyBean')
        def interceptor = getBean(context, 'annbinding2.TestInterceptor')

        when:
        instance.test()

        then:
        interceptor.count == 0

        when:
        instance.test2()

        then:
        interceptor.count == 0

        when:
        instance.testBoth()

        then:
        interceptor.count == 1

        cleanup:
        context.close()
    }

    void "test validated on class with generics"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.$BaseEntityService' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, """
package test

@io.micronaut.validation.Validated
open class BaseEntityService<T: BaseEntity>: BaseService<T>()

class BaseEntity

abstract class BaseService<T>: IBeanValidator<T> {
    override fun isValid(entity: T) = true
}

interface IBeanValidator<T> {
    fun isValid(entity: T): Boolean
}
""")

        then:
        noExceptionThrown()
        beanDefinition != null
        beanDefinition.getTypeArguments('test.BaseService')[0].type.name == 'test.BaseEntity'
    }

    void "test aop with generics"() {
        ApplicationContext context = buildContext( '''
package test

import io.micronaut.kotlin.processing.aop.simple.*
import jakarta.inject.Singleton

@Singleton
open class Test {

    @Mutating("name")
    open fun <T : CharSequence?> testGenericsWithExtends(name: T, age: Int): T {
        return "Name is $name" as T
    }

    @Mutating("name")
    open fun <T> testListWithWildCardIn(name: T, p2: CovariantClass<in String>): CovariantClass<in String> {
        return CovariantClass(name.toString())
    }

    @Mutating("name")
    open fun <T> testListWithWildCardOut(name: T, p2: CovariantClass<out String>): CovariantClass<out String> {
        return CovariantClass(name.toString())
    }
}
''', true)
        def instance = getBean(context, 'test.Test')

        expect:
        instance.testGenericsWithExtends("abc", 0) == "Name is changed"
    }

}
