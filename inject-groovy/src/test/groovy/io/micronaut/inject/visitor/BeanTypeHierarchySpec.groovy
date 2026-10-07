package io.micronaut.inject.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec

class BeanTypeHierarchySpec extends AbstractBeanDefinitionSpec {

    void "an introspection describes the hierarchy of its type when asked to"() {
        given:
        def introspection = buildBeanIntrospection('test.Child', '''
package test

import io.micronaut.core.annotation.Introspected

interface Named {
    String getName()
}

interface Titled extends Named {
    String getTitle()
}

abstract class Base<T> implements Named, Serializable {
    String getName() { "base" }
    abstract T value(T input)
}

@Introspected(hierarchy = true)
class Child extends Base<String> implements Titled {
    String title
    String value(String input) { input }
    void arrays(int[] ints, String[][] names) { }
    static void notAnInstanceMethod() { }
    private void notVisible() { }
}
''')
        def hierarchy = introspection.getTypeHierarchy().orElseThrow()
        def loader = introspection.beanType.classLoader
        def child = introspection.beanType
        def base = loader.loadClass('test.Base')
        def named = loader.loadClass('test.Named')
        def titled = loader.loadClass('test.Titled')

        expect:
        hierarchy.types.take(3) == [child, base, named]
        hierarchy.contains(Serializable)
        hierarchy.contains(titled)
        hierarchy.getSuperclass(child).get() == base
        hierarchy.getSuperclass(base).get() == Object
        !hierarchy.getSuperclass(named).isPresent()
        hierarchy.getInterfaces(child).contains(titled)
        hierarchy.getInterfaces(titled) == [named]
        hierarchy.declaresMethod('value', String)
        hierarchy.declaresMethod('arrays', int[], String[][])
        !hierarchy.declaresMethod('getName')
        !hierarchy.declaresMethod('notAnInstanceMethod')
        !hierarchy.declaresMethod('notVisible')
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
