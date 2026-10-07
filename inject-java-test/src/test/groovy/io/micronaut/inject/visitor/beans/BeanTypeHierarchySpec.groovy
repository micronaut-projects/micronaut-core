package io.micronaut.inject.visitor.beans

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.beans.BeanIntrospectionReference

class BeanTypeHierarchySpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import io.micronaut.core.annotation.Introspected;
import java.io.Serializable;
import java.util.List;

interface Named {
    String getName();
}

interface Titled extends Named {
    String getTitle();
}

abstract class Base<T> implements Named, Serializable {
    public String getName() { return "base"; }
    public abstract T value(T in);
}

@Introspected(hierarchy = true)
class Child extends Base<String> implements Titled, Comparable<Child> {
    private String title;
    public String getTitle() { return title; }
    public String value(String in) { return in; }
    public int compareTo(Child other) { return 0; }
    public void arrays(int[] ints, String[][] names, List<String>[] lists) { }
    public static void notAnInstanceMethod() { }
    private void notVisible() { }
}
'''

    void "an introspection describes the hierarchy of its type when asked to"() {
        given:
        def introspection = buildBeanIntrospection('test.Child', SOURCE)
        def hierarchy = introspection.getTypeHierarchy().orElseThrow()
        def loader = introspection.beanType.classLoader
        def child = introspection.beanType
        def base = loader.loadClass('test.Base')
        def named = loader.loadClass('test.Named')
        def titled = loader.loadClass('test.Titled')

        expect: 'every type once, depth first: the super class and its super types, then the interfaces'
        hierarchy.beanType == child
        hierarchy.types == [child, base, named, Serializable, titled, Comparable]
        hierarchy.contains(base)
        !hierarchy.contains(String)

        and: 'what each declares'
        hierarchy.getSuperclass(child).get() == base
        hierarchy.getSuperclass(base).get() == Object
        !hierarchy.getSuperclass(named).isPresent()
        hierarchy.getInterfaces(child) == [titled, Comparable]
        hierarchy.getInterfaces(base) == [named, Serializable]
        hierarchy.getInterfaces(titled) == [named]
        hierarchy.getInterfaces(String) == []

        and: 'the instance methods the type declares that are not private, erased'
        hierarchy.declaresMethod('getTitle')
        hierarchy.declaresMethod('value', String)
        hierarchy.declaresMethod('compareTo', child)
        hierarchy.declaresMethod('arrays', int[], String[][], List[])
        hierarchy.findDeclaredMethod('arrays', int[], String[][], List[]).get().returnTypeName() == 'void'
        hierarchy.findDeclaredMethod('value', String).get().returnTypeName() == 'java.lang.String'
        !hierarchy.declaresMethod('getName')
        !hierarchy.declaresMethod('notAnInstanceMethod')
        !hierarchy.declaresMethod('notVisible')
        !hierarchy.declaresMethod('value', Object)

        and: 'read once'
        introspection.getTypeHierarchy().get().is(hierarchy)
    }

    void "an introspection does not describe the hierarchy by default"() {
        when:
        def introspection = buildBeanIntrospection('test.Plain', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
class Plain implements Runnable {
    public void run() { }
}
''')

        then:
        !introspection.getTypeHierarchy().isPresent()
    }

    void "the hierarchy is generated into the introspection, not its annotation metadata"() {
        when:
        def introspection = buildBeanIntrospection('test.Bare', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected(hierarchy = true, annotationMetadata = false)
class Bare implements Runnable {
    public void run() { }
}
''')
        def hierarchy = introspection.getTypeHierarchy().orElseThrow()

        then:
        introspection.annotationMetadata.isEmpty()
        hierarchy.types == [introspection.beanType, Runnable]
        hierarchy.declaresMethod('run')
    }

    void "an imported type is described when the import asks for it"() {
        when:
        def loader = buildClassLoader('test.Imports', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected(classes = Target.class, hierarchy = true)
class Imports {
}

class Target extends Thread {
    public void work(long amount) { }
}
''')

        def target = loader.loadClass('test.Target')
        def reference = loader.loadClass('test.$test_Target$Introspection').getDeclaredConstructor().newInstance() as BeanIntrospectionReference
        def hierarchy = reference.load().getTypeHierarchy().orElseThrow()

        then:
        hierarchy.types == [target, Thread, Runnable]
        hierarchy.getSuperclass(Thread).get() == Object
        hierarchy.declaresMethod('work', long)
        !hierarchy.declaresMethod('run')
    }
}
