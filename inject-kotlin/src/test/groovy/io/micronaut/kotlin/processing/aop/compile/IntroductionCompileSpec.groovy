package io.micronaut.kotlin.processing.aop.compile

import io.micronaut.annotation.processing.test.KotlinCompiler
import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.buildContext
import static io.micronaut.annotation.processing.test.KotlinCompiler.getBean
import static io.micronaut.annotation.processing.test.KotlinCompiler.getBeanDefinition

class IntroductionCompileSpec extends Specification {

    void "test inherited default methods are not overridden"() {
        given:
            ApplicationContext context = KotlinCompiler.buildContext('''
package introductiontest

import java.lang.annotation.*
import io.micronaut.aop.*
import jakarta.inject.*

@TestAnn
interface MyBean : Parent {

    fun test(): Int

    fun getName() : String {
        return "my-bean"
    }

    @Override
    override fun getDescription() : String {
        return "description"
    }
}

interface Parent {
     fun getParentName() : String {
        return "parent"
    }

    fun getDescription() : String
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.CLASS)
@Introduction
annotation class TestAnn

@InterceptorBean(TestAnn::class)
class StubIntroduction : Interceptor<Any, Any> {
    var invoked: Int = 0

    override fun intercept(context: InvocationContext<Any, Any>) : Any {
        invoked++
        return 10
    }
}

''')
            def instance = getBean(context, 'introductiontest.MyBean')
            def interceptor = getBean(context, 'introductiontest.StubIntroduction')

        when:
            def result = instance.test()

        then:"the interceptor was invoked"
            instance instanceof Intercepted
            instance.name == 'my-bean'
            instance.description == 'description'
            instance.parentName == 'parent'
            result == 10
            interceptor.invoked == 1
    }

    void "test inherited default or abstract methods are not overridden"() {
        given:
            ApplicationContext context = KotlinCompiler.buildContext('''
package introductiontest2

import java.lang.annotation.*
import io.micronaut.aop.*
import jakarta.inject.*

@TestAnn
abstract class MyBean : Parent(), MyInterface {

    abstract fun test() : Int

    fun getName() : String {
        return "my-bean"
    }

    @Override
    override fun getDescription() : String {
        return "description"
    }
}

abstract class Parent {
    fun getParentName() : String {
        return "parent"
    }

    abstract fun getDescription() : String
}

interface MyInterface {

    fun getDescription() : String
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.CLASS)
@Introduction
annotation class TestAnn

@InterceptorBean(TestAnn::class)
class StubIntroduction : Interceptor<Any, Any> {
    var invoked: Int = 0

    override fun intercept(context: InvocationContext<Any, Any>) : Any {
        invoked++
        return 10
    }
}

''')
            def instance = getBean(context, 'introductiontest2.MyBean')
            def interceptor = getBean(context, 'introductiontest2.StubIntroduction')

        when:
            def result = instance.test()

        then:"the interceptor was invoked"
            instance instanceof Intercepted
            instance.name == 'my-bean'
            instance.description == 'description'
            instance.parentName == 'parent'
            result == 10
            interceptor.invoked == 1
    }

    void 'test coroutine repository'() {
        given:
        def context = buildContext('''
package test

import io.micronaut.aop.Introduction
import jakarta.inject.Singleton

import kotlinx.coroutines.flow.Flow

class SomeEntity

interface CoroutineCrudRepository<E, ID> {

    suspend fun <S : E> save(entity: S): S
    suspend fun <S : E> update(entity: S): S
    fun <S : E> updateAll(entities: Iterable<S>): Flow<S>
    fun <S : E> saveAll(entities: Iterable<S>): Flow<S>
    suspend fun findById(id: ID): E?
    suspend fun existsById(id: ID): Boolean
    fun findAll(): Flow<E>
    suspend fun count(): Long
    suspend fun deleteById(id: ID): Int
    suspend fun delete(entity: E): Int
    suspend fun deleteAll(entities: Iterable<E>): Int
    suspend fun deleteAll(): Int
}

@MyRepository
interface CustomRepository : CoroutineCrudRepository<SomeEntity, Long> {

    // As of Kotlin version 1.7.20 and KAPT, this will generate JVM signature: "SomeEntity findById(long id, continuation)"
    override suspend fun findById(id: Long): SomeEntity?

    suspend fun xyz(): String

    suspend fun abc(): String

    suspend fun count1(): String

    suspend fun count2(): String

}

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Introduction
@Singleton
annotation class MyRepository
''')
        def definition = getBeanDefinition(context, 'test.CustomRepository')

        expect:
        definition != null

        cleanup:
        context.close()
    }

    void 'test apply introduction advise with interceptor binding'() {
        given:
        ApplicationContext context = buildContext('''
package introductiontest

import io.micronaut.aop.*
import jakarta.inject.Singleton

@TestAnn
interface MyBean {
    fun test(): Int
    val test : Int
}

@Retention
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Introduction
annotation class TestAnn

@InterceptorBean(TestAnn::class)
class StubIntroduction: Interceptor<Any, Any> {
    var invoked = 0
    override fun intercept(context: InvocationContext<Any, Any>): Any {
        invoked++
        return 10
    }
}
''')
        def instance = getBean(context, 'introductiontest.MyBean')
        def interceptor = getBean(context, 'introductiontest.StubIntroduction')

        when:
        def result = instance.test()

        then:"the interceptor was invoked"
        instance instanceof Intercepted
        interceptor.invoked == 1
        result == 10

        when:
        result = instance.test

        then:"the interceptor was invoked"
        instance instanceof Intercepted
        interceptor.invoked == 2
        result == 10
    }

    void 'test intercept list property'() {
        given:
        ApplicationContext context = buildContext('''
package introductiontest

import io.micronaut.aop.*
import jakarta.inject.Singleton

@TestAnn
interface MyBean {
    val test : List<String>
}

@Retention
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Introduction
annotation class TestAnn

@InterceptorBean(TestAnn::class)
class StubIntroduction: Interceptor<Any, List<Object>> {
    var invoked : Boolean = false
    override fun intercept(context: InvocationContext<Any, List<Object>>): List<Object> {
        invoked = true
        return emptyList()
    }
}
''')
        def instance = getBean(context, 'introductiontest.MyBean')
        def interceptor = getBean(context, 'introductiontest.StubIntroduction')

        when:
        def result = instance.test

        then:"the interceptor was invoked"
        instance instanceof Intercepted
        interceptor.invoked
        result == []
    }
}
