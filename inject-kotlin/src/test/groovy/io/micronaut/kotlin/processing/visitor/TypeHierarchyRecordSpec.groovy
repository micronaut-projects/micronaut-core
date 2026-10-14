package io.micronaut.kotlin.processing.visitor

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.core.type.TypeHierarchies
import io.micronaut.core.type.TypeHierarchy


import static io.micronaut.kotlin.processing.visitor.TypeStrings.string
import static io.micronaut.kotlin.processing.visitor.TypeStrings.strings

class TypeHierarchyRecordSpec extends AbstractKotlinCompilerSpec {

    static TypeHierarchy record(ClassLoader classLoader, String typeName) {
        TypeHierarchies.invalidate()
        TypeHierarchies.find(typeName, classLoader).orElseThrow { new AssertionError("No record of " + typeName) }
    }

    static int size(ClassLoader classLoader, String recordName) {
        classLoader.getResourceAsStream(recordName.replace('.', '/') + ".class").bytes.length
    }

    void "a generic class with a bounded variable is found through the service entries"() {
        given:
        def classLoader = buildClassLoader('test.Payload', '''
package test

import io.micronaut.core.annotation.RecordTypeHierarchy
import java.util.function.Supplier

@RecordTypeHierarchy
open class Payload<T> : ArrayList<T>(), Supplier<List<out T>> where T : Number, T : Comparable<T> {
    override fun get(): List<out T> = this
}
''')
        def hierarchy = record(classLoader, 'test.Payload')

        expect:
        hierarchy.type == classLoader.loadClass('test.Payload')
        hierarchy.typeVariables.size() == 1
        hierarchy.typeVariables[0] instanceof GenericPlaceholder
        hierarchy.typeVariables[0].variableName == 'T'
        hierarchy.typeVariables[0].type == Number
        strings(hierarchy.typeVariables[0].bounds) == ['Number', 'Comparable<T>']
        string(hierarchy.genericSuperclass.get()) == 'ArrayList<T>'
        hierarchy.genericSuperclass.get().typeParameters[0].isUnresolvedTypeVariable()
        strings(hierarchy.genericInterfaces) == ['Supplier<List<? extends T>>']
        string(hierarchy.declaration) == 'Payload<T>'

        when:
        def closure = hierarchy.getTypeClosure(Argument.of(hierarchy.type, Integer))

        then:
        strings(closure) - ['SequencedCollection<Integer>'] == ['Payload<Integer>', 'ArrayList<Integer>', 'AbstractList<Integer>', 'AbstractCollection<Integer>', 'Collection<Integer>', 'Iterable<Integer>',
                             'List<Integer>', 'RandomAccess', 'Cloneable', 'Serializable', 'Supplier<List<? extends Integer>>', 'Object']
        println('record bytes test.Payload: ' + size(classLoader, TypeHierarchies.recordClassName('test.Payload', 'test')))
        size(classLoader, TypeHierarchies.recordClassName('test.Payload', 'test')) < 4096
    }

    void "diamond interfaces are deduplicated and a requested JDK class is recorded"() {
        given:
        def classLoader = buildClassLoader('test.Diamond', '''
package test

import io.micronaut.core.annotation.RecordTypeHierarchy

interface Top<T>
interface Left<T> : Top<T>
interface Right<T> : Top<T>

@RecordTypeHierarchy(classes = [Top::class, Left::class, Right::class, Diamond::class, java.util.ArrayList::class, java.util.AbstractList::class,
    java.util.List::class, java.util.Collection::class, java.lang.Iterable::class, java.util.AbstractCollection::class])
class Diamond : java.util.ArrayList<Int>(), Left<String>, Right<String>
''')
        def diamond = record(classLoader, 'test.Diamond')
        def arrayList = record(classLoader, 'java.util.ArrayList')

        expect:
        diamond.typeVariables.empty
        string(diamond.genericSuperclass.get()) == 'ArrayList<Integer>'
        strings(diamond.genericInterfaces) == ['Left<String>', 'Right<String>']
        arrayList.type == ArrayList
        arrayList.typeVariables*.variableName == ['E']
        string(arrayList.genericSuperclass.get()) == 'AbstractList<E>'
        strings(arrayList.genericInterfaces) as Set == ['List<E>', 'RandomAccess', 'Cloneable', 'Serializable'] as Set

        when:
        def closure = diamond.getTypeClosure(Argument.of(diamond.type))

        then: "KSP reads the interfaces of a class of the JDK in its own order, and List adds interfaces in later JDKs"
        strings(closure) - ['SequencedCollection<Integer>'] as Set == ['Diamond', 'ArrayList<Integer>', 'AbstractList<Integer>', 'AbstractCollection<Integer>', 'Collection<Integer>', 'Iterable<Integer>',
                             'List<Integer>', 'RandomAccess', 'Cloneable', 'Serializable', 'Left<String>', 'Top<String>', 'Right<String>', 'Object'] as Set
        strings(closure).first() == 'Diamond'
        strings(closure).last() == 'Object'
        println('record bytes java.util.ArrayList: ' + size(classLoader, TypeHierarchies.recordClassName('java.util.ArrayList', 'test')) + ', test.Diamond: ' + size(classLoader, TypeHierarchies.recordClassName('test.Diamond', 'test')) + ', test.Top: ' + size(classLoader, TypeHierarchies.recordClassName('test.Top', 'test')))
        size(classLoader, TypeHierarchies.recordClassName('java.util.ArrayList', 'test')) < 4096

        and: "a type without a record"
        TypeHierarchies.find('test.Missing', classLoader).empty
    }

    void "a generic array argument"() {
        given:
        def classLoader = buildClassLoader('test.Payload', '''
package test

import io.micronaut.core.annotation.RecordTypeHierarchy
import java.util.function.Supplier

@RecordTypeHierarchy
class Payload<T> : Supplier<Array<T>> {
    override fun get(): Array<T> = throw UnsupportedOperationException()
}
''')
        def hierarchy = record(classLoader, 'test.Payload')

        expect:
        strings(hierarchy.genericInterfaces) == ['Supplier<T[]>']
        hierarchy.genericInterfaces[0].typeParameters[0].type == Object[]
        hierarchy.genericInterfaces[0].typeParameters[0].isTypeVariable()

        when:
        def closure = hierarchy.getTypeClosure(Argument.of(hierarchy.type, String))

        then:
        strings(closure) == ['Payload<String>', 'Supplier<String[]>', 'Object']
        closure[1].typeParameters[0].type == String[]
    }

    void "a private class is recorded"() {
        given:
        def classLoader = buildClassLoader('test.Holder', '''
package test

import io.micronaut.core.annotation.RecordTypeHierarchy

@RecordTypeHierarchy
private class Hidden<T> : java.util.function.Supplier<T> {
    override fun get(): T = throw UnsupportedOperationException()
}
''')
        def hidden = record(classLoader, 'test.Hidden')

        expect:
        hidden.type == classLoader.loadClass('test.Hidden')
        strings(hidden.genericInterfaces) == ['Supplier<T>']
    }

    void "the same type is recorded once"() {
        given:
        def classLoader = buildClassLoader('test.One', '''
package test

import io.micronaut.core.annotation.RecordTypeHierarchy

@RecordTypeHierarchy(classes = [java.util.ArrayList::class, java.util.ArrayList::class])
class One

@RecordTypeHierarchy(classes = [java.util.ArrayList::class])
class Two
''')

        expect:
        record(classLoader, 'java.util.ArrayList').type == ArrayList
    }
}
