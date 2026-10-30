package io.micronaut.kotlin.processing.visitor

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.type.TypeHierarchies

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
        hierarchy.getDeclaringTypes(methods.value) == [child, base]
        hierarchy.getDeclaringTypes(methods.own) == [child]
        hierarchy.getDeclaringTypes(methods.name) == [base, named]
    }

    void "an inherited method lists an interface the introspected type introduces"() {
        when:
        def introspection = buildBeanIntrospection('test.Child', '''
package test

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected

interface Deep {
    fun run(): String
}

interface Contract {
    fun run(): String
}

open class Parent : Deep {
    @Executable
    override fun run(): String = "parent"
}

@Introspected(hierarchy = true)
open class Child : Parent(), Contract
''')
        def hierarchy = introspection.getTypeHierarchy().orElseThrow()
        def loader = introspection.beanType.classLoader
        def (parent, deep, contract) = ['Parent', 'Deep', 'Contract'].collect { loader.loadClass('test.' + it) }
        def run = introspection.beanMethods.find { it.name == 'run' }

        then:
        hierarchy.types == [introspection.beanType, parent, deep, contract]
        hierarchy.getDeclaringTypes(run) == [parent, contract, deep]
        !hierarchy.isDeclared(run)
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
    void "the generic hierarchy of the introspected type is recorded beside the introspection"() {
        given:
        def introspection = buildBeanIntrospection('test.Child', '''
package test

import io.micronaut.core.annotation.Introspected
import java.util.function.Supplier

abstract class Base<T> : Supplier<T>

@Introspected(hierarchy = true)
class Child<T : Number> : Base<T>(), Comparable<Child<T>> {
    override fun get(): T = throw UnsupportedOperationException()
    override fun compareTo(other: Child<T>): Int = 0
}
''')
        def hierarchy = introspection.getTypeHierarchy().orElseThrow()
        TypeHierarchies.invalidate()
        def record = hierarchy.genericHierarchy.orElseThrow()

        expect: "the record is found through the service entries of the compiled classes"
        record.type == introspection.beanType
        record.typeVariables*.name == ['T']
        record.typeVariables[0].type == Number
        record.genericSuperclass.get().type == introspection.beanType.classLoader.loadClass('test.Base')
        record.genericSuperclass.get().typeParameters[0].isUnresolvedTypeVariable()
        record.genericInterfaces*.type == [Comparable]

        and: "the closure keeps the variables of the type and the arguments of its super types"
        hierarchy.typeClosure*.type == [introspection.beanType, introspection.beanType.classLoader.loadClass('test.Base'), java.util.function.Supplier, Comparable, Object]
        hierarchy.typeClosure[1].typeParameters[0].isUnresolvedTypeVariable()
        hierarchy.typeClosure[2].typeParameters[0].isUnresolvedTypeVariable()
        hierarchy.typeClosure[3].typeParameters[0].type == introspection.beanType
    }
}
