package io.micronaut.kotlin.processing.beans.executable

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.*

class KotlinInheritedDefaultArgumentsSpec extends Specification {

    void "an inherited method with default arguments that implements an interface the owning type introduces keeps its defaults"() {
        given:
        ApplicationContext context = buildContext('''
package test

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton

interface Contract {
    fun greet(name: String): String
}

open class Parent {

    open fun greet(name: String = "World"): String = "Hello $name"
}

@Singleton
@Executable
open class Child : Parent(), Contract
''')
        def definition = getBeanDefinition(context, 'test.Child')
        def bean = getBean(context, 'test.Child')
        def method = definition.executableMethods.find { it.methodName == 'greet' }

        expect:
        method.invoke(bean, "Fred") == "Hello Fred"

        and: "an absent argument gets the default declared by the superclass"
        method.invoke(bean, [null] as Object[]) == "Hello World"

        cleanup:
        context.close()
    }
}
