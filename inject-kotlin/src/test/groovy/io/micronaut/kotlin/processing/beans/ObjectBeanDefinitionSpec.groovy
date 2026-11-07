package io.micronaut.kotlin.processing.beans

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec

class ObjectBeanDefinitionSpec extends AbstractKotlinCompilerSpec {

    void "test singleton object bean is the INSTANCE"() {
        when:
        def context = buildContext("""
package test

import jakarta.inject.Singleton

@Singleton
object Foo {
    fun hello() = "hello"
}
""")
        def type = context.classLoader.loadClass("test.Foo")
        def instance = type.getField("INSTANCE").get(null)

        then:
        context.getBean(type).is(instance)

        cleanup:
        context.close()
    }

    void "test factory object is the INSTANCE and produces beans"() {
        when:
        def context = buildContext("""
package test

import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

class Bar

@Factory
object BarFactory {
    @Singleton
    fun bar(): Bar = Bar()
}
""")
        def factoryType = context.classLoader.loadClass("test.BarFactory")
        def instance = factoryType.getField("INSTANCE").get(null)

        then:
        context.getBean(context.classLoader.loadClass("test.Bar")) != null
        context.getBean(factoryType).is(instance)

        cleanup:
        context.close()
    }

    void "test singleton object with an injected member"() {
        when:
        def context = buildContext("""
package test

import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class Dependency

@Singleton
object Foo {
    @Inject
    lateinit var dependency: Dependency
}
""")
        def type = context.classLoader.loadClass("test.Foo")
        def instance = type.getField("INSTANCE").get(null)
        def dependency = context.getBean(context.classLoader.loadClass("test.Dependency"))

        then:
        context.getBean(type).is(instance)
        instance.dependency.is(dependency)

        cleanup:
        context.close()
    }

    void "test #annotation on an object is a compilation error"() {
        when:
        buildBeanDefinition("test.Foo", """
package test

import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Prototype
import jakarta.inject.Singleton

@Singleton
class Dependency

$declaration
object Foo {
    var x: String? = null
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("A singleton object declaration (Kotlin object) has exactly one instance and cannot be declared with @EachProperty, @EachBean or @Prototype")

        where:
        annotation      | declaration
        "@EachProperty" | '@EachProperty("foos")'
        "@EachBean"     | '@EachBean(Dependency::class)'
        "@Prototype"    | '@Prototype'
    }

    void "test factory object with @EachBean and @Prototype methods resolves its beans"() {
        when:
        def context = buildContext("""
package test

import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Prototype
import jakarta.inject.Singleton

@Singleton
class Dependency

class Each

class Proto

@Factory
object BarFactory {
    @EachBean(Dependency::class)
    fun each(dependency: Dependency): Each = Each()

    @Prototype
    fun proto(): Proto = Proto()
}
""")
        def protoType = context.classLoader.loadClass("test.Proto")
        def eachType = context.classLoader.loadClass("test.Each")

        then:
        !context.getBean(protoType).is(context.getBean(protoType))
        context.getBeansOfType(eachType).size() == 1

        cleanup:
        context.close()
    }
}
