package io.micronaut.kotlin.processing.visitor

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec

class BeanTypeHierarchySpec extends AbstractKotlinCompilerSpec {

    void "an introspection describes the hierarchy of its type when asked to"() {
        given:
        def introspection = buildBeanIntrospection('test.Child', '''
package test

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected

interface Named {
    fun name(): String
}

interface Titled : Named {
    fun title(): String
}

abstract class Base<T> : Named, java.io.Serializable {
    @Executable
    override fun name(): String = "base"
    @Executable
    abstract fun value(input: T): T
}

@Introspected(hierarchy = true)
open class Child : Base<String>(), Titled {
    override fun title(): String = "title"
    @Executable
    override fun value(input: String): String = input
    @Executable
    fun own(ints: IntArray) { }
}
''')
        def hierarchy = introspection.getTypeHierarchy().orElseThrow()
        def loader = introspection.beanType.classLoader
        def child = introspection.beanType
        def base = loader.loadClass('test.Base')
        def named = loader.loadClass('test.Named')
        def titled = loader.loadClass('test.Titled')
        def methods = introspection.beanMethods.collectEntries { [it.name, it] }

        expect:
        hierarchy.types == [child, base, named, Serializable, titled]
        hierarchy.getSuperclass(child).get() == base
        hierarchy.getSuperclass(base).get() == Object
        !hierarchy.getSuperclass(named).isPresent()
        hierarchy.getInterfaces(child) == [titled]
        hierarchy.getInterfaces(base) == [named, Serializable]
        hierarchy.getInterfaces(titled) == [named]
        hierarchy.isDeclared(methods.value)
        hierarchy.isDeclared(methods.own)
        !hierarchy.isDeclared(methods.name)
    }

    void "an introspection does not describe the hierarchy by default"() {
        expect:
        !buildBeanIntrospection('test.Plain', '''
package test

import io.micronaut.core.annotation.Introspected

@Introspected
class Plain : Runnable {
    override fun run() { }
}
''').getTypeHierarchy().isPresent()
    }
}
