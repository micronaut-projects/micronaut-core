package io.micronaut.inject.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec

class BeanTypeHierarchySpec extends AbstractBeanDefinitionSpec {

    void "an introspection describes the hierarchy of its type when asked to"() {
        given:
        def introspection = buildBeanIntrospection('test.Child', '''
package test

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected

interface Named {
    String getName()
}

interface Titled extends Named {
    String getTitle()
}

abstract class Base<T> implements Named, Serializable {
    @Executable
    String getName() { "base" }
    @Executable
    abstract T value(T input)
}

@Introspected(hierarchy = true)
class Child extends Base<String> implements Titled {
    String title
    @Executable
    String value(String input) { input }
    @Executable
    void own(int[] ints) { }
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
        hierarchy.types.take(3) == [child, base, named]
        hierarchy.contains(Serializable)
        hierarchy.contains(titled)
        hierarchy.getSuperclass(child).get() == base
        hierarchy.getSuperclass(base).get() == Object
        !hierarchy.getSuperclass(named).isPresent()
        hierarchy.getInterfaces(child).contains(titled)
        hierarchy.getInterfaces(titled) == [named]
        hierarchy.isDeclared(methods.value)
        hierarchy.isDeclared(methods.own)
        !hierarchy.isDeclared(methods.getName)
        hierarchy.getDeclaringTypes(methods.value) == [child, base]
        hierarchy.getDeclaringTypes(methods.own) == [child]
        hierarchy.getDeclaringTypes(methods.getName) == [base, named]
    }

    void "an inherited method lists an interface the introspected type introduces"() {
        when:
        def introspection = buildBeanIntrospection('test.Child', '''
package test

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected

interface Deep {
    String run()
}

interface Contract {
    String run()
}

class Parent implements Deep {
    @Executable
    String run() { "parent" }
}

@Introspected(hierarchy = true)
class Child extends Parent implements Contract {
}
''')
        def hierarchy = introspection.getTypeHierarchy().orElseThrow()
        def loader = introspection.beanType.classLoader
        def (parent, deep, contract) = ['Parent', 'Deep', 'Contract'].collect { loader.loadClass('test.' + it) }
        def run = introspection.beanMethods.find { it.name == 'run' }

        then:
        hierarchy.getDeclaringTypes(run) == [parent, contract, deep]
        !hierarchy.isDeclared(run)
    }

    void "an introspection does not describe the hierarchy by default"() {
        expect:
        !buildBeanIntrospection('test.Plain', '''
package test

import io.micronaut.core.annotation.Introspected

@Introspected
class Plain implements Runnable {
    void run() { }
}
''').getTypeHierarchy().isPresent()
    }
}
