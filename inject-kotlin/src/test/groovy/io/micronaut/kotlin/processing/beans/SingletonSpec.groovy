package io.micronaut.kotlin.processing.beans

import io.micronaut.inject.BeanDefinition
import io.micronaut.kotlin.processing.KotlinCompiler
import spock.lang.Specification

class SingletonSpec extends Specification {

    void "test simple singleton bean"() {
        when:
        def context = KotlinCompiler.buildContext("""
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
        def context = KotlinCompiler.buildContext("""
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
        def context = KotlinCompiler.buildContext("""
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
        BeanDefinition beanDefinition = KotlinCompiler.buildBeanDefinition('test.AbstractBean', '''
package test

import jakarta.inject.Singleton

@Singleton
abstract class AbstractBean {

}
''')
        then:
        beanDefinition.isAbstract()
    }
}
