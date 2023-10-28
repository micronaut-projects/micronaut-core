package io.micronaut.kotlin.processing.beans.executable.inheritance

import io.micronaut.inject.BeanDefinition
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.buildBeanDefinition

class InheritedExecutableSpec extends Specification {

    void "test extending an abstract class with an executable method"() {
        given:
        BeanDefinition definition = buildBeanDefinition("test.GenericController", """
package test

import io.micronaut.context.annotation.Executable

abstract class GenericController<T> {

    abstract fun getPath(): String

    @Executable
    fun save(entity: T): String {
        return "parent"
    }
}

""")
        expect:
        definition == null
    }

    void "test the same method isn't written twice"() {
        BeanDefinition definition = buildBeanDefinition("test.StatusController", """
package test

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton

@Executable
@Singleton
class StatusController: GenericController<String>() {

    override fun getPath(): String {
        return "/statuses"
    }

    override fun save(entity: String): String {
        return "child"
    }

}

abstract class GenericController<T> {

    abstract fun getPath(): String

    @Executable
    open fun save(entity: T): String {
        return "parent"
    }

    @Executable
    open fun save(): String {
        return "parent"
    }
}

""")
        expect:
        definition != null
        definition.getExecutableMethods().any { it.methodName == "getPath" }
        definition.getExecutableMethods().any { it.methodName == "save" && it.argumentTypes == [String] as Class[] }
        definition.getExecutableMethods().any { it.methodName == "save" && it.argumentTypes.length == 0 }
        definition.getExecutableMethods().size() == 3
    }

    void "test with multiple generics"() {
        BeanDefinition definition = buildBeanDefinition("test.StatusController","""
package test

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton
import java.io.Serializable

abstract class GenericController<T, ID: Serializable> {

    @Executable
    fun save(entity: T): T {
        return entity
    }

    @Executable
    fun find(id: ID): T? {
        return null
    }

    abstract fun create(id: ID): T
}

@Executable
@Singleton
class StatusController: GenericController<String, Int>() {

    override fun create(id: Int): String {
        return id.toString()
    }
}
""")
        expect:
        definition != null
        definition.getExecutableMethods().any { it.methodName == "create" && it.argumentTypes == [int] as Class[] }
        definition.getExecutableMethods().any { it.methodName == "save" && it.argumentTypes == [String] as Class[] }
        definition.getExecutableMethods().any { it.methodName == "find" && it.argumentTypes == [Integer] as Class[] }
        definition.getExecutableMethods().size() == 3
    }

    void "test multiple inheritance"() {
        BeanDefinition definition = buildBeanDefinition("test.Z", """
package test

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton

interface X {

    @Executable
    fun test()
}

abstract class Y : X {

    override fun test() {
    }
}

@Singleton
class Z : Y() {

    override fun test() {
    }
}
""")
        expect:
        definition != null
        definition.executableMethods.size() == 1
    }
}
