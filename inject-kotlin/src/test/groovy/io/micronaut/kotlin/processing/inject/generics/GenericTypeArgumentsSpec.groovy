package io.micronaut.kotlin.processing.inject.generics

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.context.BeanContext
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.inject.BeanDefinition
import spock.lang.Unroll

import jakarta.validation.ConstraintViolationException
import java.util.function.Function
import java.util.function.Supplier

class GenericTypeArgumentsSpec extends AbstractKotlinCompilerSpec {
    void "test generic type arguments with inner classes resolve"() {
        given:
        def definition = buildBeanDefinition('innergenerics.Outer$FooImpl', '''
package innergenerics

class Outer {

    interface Foo<T : CharSequence>

    @jakarta.inject.Singleton
    class FooImpl : Foo<String>
}
''')
        def itfe = definition.beanType.classLoader.loadClass('innergenerics.Outer$Foo')

        expect:
        definition.getTypeParameters(itfe).length == 1
    }

    void "test type arguments with inherited fields"() {
        given:
        BeanContext context = buildContext('inheritedfields.UserDaoClient', '''
package inheritedfields

import jakarta.inject.*

@Singleton
class UserDaoClient : DaoClient<User>()

@Singleton
class UserDao : Dao<User>()
class User

open class DaoClient<T> {

    @Inject
    lateinit var dao : Dao<T>
}

open class Dao<T>

@Singleton
class FooDao : Dao<Foo>()
class Foo
''')
        def definition = getBeanDefinition(context, 'inheritedfields.UserDaoClient')

        expect:
        definition.injectedMethods.first().arguments[0].typeParameters.length == 1
        definition.injectedMethods.first().arguments[0].typeParameters[0].type.simpleName == "User"
        getBean(context, 'inheritedfields.UserDaoClient').dao.getClass().simpleName == 'UserDao'
    }

    void "test type arguments for exception handler"() {
        given:
        BeanDefinition definition = buildBeanDefinition('exceptionhandler.Test', '''\
package exceptionhandler

import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*
import jakarta.validation.ConstraintViolationException

@Context
class Test : ExceptionHandler<ConstraintViolationException, java.util.function.Supplier<Foo>?> {
    override fun handle(request : String, e: ConstraintViolationException) : java.util.function.Supplier<Foo>? {
        return null
    }
}

class Foo
interface ExceptionHandler<T : Throwable, R> {
    fun handle(request : String, exception : T) : R
}
''')
        expect:
        definition != null
        def typeArgs = definition.getTypeArguments("exceptionhandler.ExceptionHandler")
        typeArgs.size() == 2
        typeArgs[0].type == ConstraintViolationException
        typeArgs[1].type == Supplier
    }

    void "test type arguments for factory returning interface"() {
        given:
        BeanDefinition definition = buildBeanDefinition('factorygenerics.Test$MyFunc0', '''\
package factorygenerics

import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*
import io.micronaut.context.event.*

@Factory
class Test {
    @Bean
    fun myFunc() : BeanCreatedEventListener<Foo> {
        return BeanCreatedEventListener { event -> event.getBean() }
    }
}

interface Foo

''')
        expect:
        definition != null
        definition.getTypeArguments(BeanCreatedEventListener).size() == 1
        definition.getTypeArguments(BeanCreatedEventListener)[0].type.name == 'factorygenerics.Foo'
    }

    @Unroll
    void "test generic return type resolution for return type: #returnType"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test', """\
package test

import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*
import java.util.*

@jakarta.inject.Singleton
class Test {

    @Executable
    fun test() : $returnType? {
        return null
    }
}
""")
        def method = definition.getRequiredMethod("test")

        expect:
        method.getDescription(true).startsWith("$returnType" )

        where:
        returnType <<
                ['List<Map<String, Integer>>',
                 'List<List<String>>',
                 'List<String>',
                 'Map<String, Integer>']
    }

    void "test type arguments for interface"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test', '''\
package test

import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*

@jakarta.inject.Singleton
class Test : java.util.function.Function<String, Int>{

    override fun apply(str : String) : Int {
        return 10
    }
}

class Foo
''')
        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }

    void "test type arguments for inherited interface"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test', '''\
package test

import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*

@jakarta.inject.Singleton
class Test : Foo {

    override fun apply(str : String) : Int {
        return 10
    }
}

interface Foo : java.util.function.Function<String, Int>
''')
        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }


    void "test type arguments for inherited interface 2"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test', '''\
package test

import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*

@jakarta.inject.Singleton
class Test : Bar {

    override fun apply(str : String) : Int {
        return 10
    }
}

interface Bar : Foo<Int>
interface Foo<A> : java.util.function.Function<String, A>
''')
        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }

    void "test type arguments for inherited interface - using same name as another type parameter"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test', '''\
package test

import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*

@jakarta.inject.Singleton
class Test : Bar {

    override fun apply(str : String) : Int {
        return 10
    }
}

interface Bar : Foo<Int>
interface Foo<T> : java.util.function.Function<String, T>
''')
        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }

    void "test type arguments for factory with inheritance"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test$MyFunc0', '''\
package test

import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*

@Factory
class Test {

    @Bean
    fun myFunc() : Foo {
        return object : Foo {
            override fun apply(t: String): Int {
                return 10
            }
        }
    }
}

interface Foo : java.util.function.Function<String, Int>

''')
        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }
}
