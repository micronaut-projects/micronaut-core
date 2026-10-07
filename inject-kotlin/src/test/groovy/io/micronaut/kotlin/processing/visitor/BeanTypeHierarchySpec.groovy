package io.micronaut.kotlin.processing.visitor

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec

class BeanTypeHierarchySpec extends AbstractKotlinCompilerSpec {

    void "an introspection describes the hierarchy of its type when asked to"() {
        given:
        def introspection = buildBeanIntrospection('test.Child', '''
package test

import io.micronaut.core.annotation.Introspected

interface Named {
    fun name(): String
}

interface Titled : Named {
    fun title(): String
}

abstract class Base<T> : Named, java.io.Serializable {
    override fun name(): String = "base"
    abstract fun value(input: T): T
}

@Introspected(hierarchy = true)
open class Child : Base<String>(), Titled {
    override fun title(): String = "title"
    override fun value(input: String): String = input
    fun arrays(ints: IntArray, names: Array<Array<String>>) { }
    private fun notVisible() { }
}
''')
        def hierarchy = introspection.getTypeHierarchy().orElseThrow()
        def loader = introspection.beanType.classLoader
        def child = introspection.beanType
        def base = loader.loadClass('test.Base')
        def named = loader.loadClass('test.Named')
        def titled = loader.loadClass('test.Titled')

        expect:
        hierarchy.types == [child, base, named, Serializable, titled]
        hierarchy.getSuperclass(child).get() == base
        hierarchy.getSuperclass(base).get() == Object
        !hierarchy.getSuperclass(named).isPresent()
        hierarchy.getInterfaces(child) == [titled]
        hierarchy.getInterfaces(base) == [named, Serializable]
        hierarchy.getInterfaces(titled) == [named]
        hierarchy.declaresMethod('title')
        hierarchy.declaresMethod('value', String)
        hierarchy.declaresMethod('arrays', int[], String[][])
        !hierarchy.declaresMethod('name')
        !hierarchy.declaresMethod('notVisible')
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
