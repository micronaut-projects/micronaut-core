package io.micronaut.kotlin.processing.beans

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.BeanDefinition

import spock.lang.Specification

class SingletonSpec extends AbstractKotlinCompilerSpec {

    void "test simple singleton bean"() {
        when:
        def context = buildContext("""
package test

import jakarta.inject.Singleton

@Singleton
class Test
""")

        then:
        noExceptionThrown()

        when:
        Class<?> test = context.classLoader.loadClass("test.Test")
        context.getBean(test)

        then:
        noExceptionThrown()
    }

    void "test singleton bean from a factory property"() {
        when:
        def context = buildContext("""
package test

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

@Factory
class Test {

    @Singleton
    @Bean
    val one = Foo("one")

}

class Foo(val name: String)
""")

        then:
        noExceptionThrown()
        Class<?> foo = context.classLoader.loadClass("test.Foo")
        context.getBean(foo).getName() == "one"
    }

    void "test singleton bean from a factory method"() {
        when:
        def context = buildContext("""
package test

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

@Factory
class Test {
    @Singleton
    fun one() = Foo("one")
}

class Foo(val name: String)
""")

        then:
        noExceptionThrown()
        Class<?> foo = context.classLoader.loadClass("test.Foo")
        context.getBean(foo).getName() == "one"
    }

    void "test singleton abstract class"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.AbstractBean', '''
package test

import jakarta.inject.Singleton

@Singleton
abstract class AbstractBean {

}
''')
        then:
        beanDefinition.isAbstract()
    }

    void "test that using @Singleton on an enum results in a compilation error"() {
        when:
        buildBeanDefinition('test.Test','''\
package test

@jakarta.inject.Singleton
enum class Test
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains('Enum types cannot be defined as beans')
    }
}
