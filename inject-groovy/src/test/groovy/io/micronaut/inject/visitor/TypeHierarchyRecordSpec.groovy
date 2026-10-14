package io.micronaut.inject.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.core.type.TypeHierarchies
import io.micronaut.core.type.TypeHierarchy

import java.util.function.Function

import static io.micronaut.inject.visitor.TypeStrings.string
import static io.micronaut.inject.visitor.TypeStrings.strings

class TypeHierarchyRecordSpec extends AbstractBeanDefinitionSpec {

    static TypeHierarchy record(ClassLoader classLoader, String typeName, String recordPackage = typeName.substring(0, typeName.lastIndexOf('.'))) {
        (TypeHierarchy) classLoader.loadClass(TypeHierarchies.recordClassName(typeName, recordPackage)).getConstructor().newInstance()
    }

    static int size(ClassLoader classLoader, String recordName) {
        ((Map<String, byte[]>) classLoader.generatedClasses)[recordName].length
    }

    static Function<Class<?>, Optional<TypeHierarchy>> resolver(ClassLoader classLoader) {
        return { Class<?> type ->
            try {
                Optional.of(record(classLoader, type.name, 'test'))
            } catch (ClassNotFoundException e) {
                Optional.empty()
            }
        } as Function
    }

    void "a generic class with a bounded variable"() {
        given:
        def classLoader = buildClassLoader('''
package test

import io.micronaut.core.annotation.RecordTypeHierarchy
import java.util.function.Supplier

@RecordTypeHierarchy
class Payload<T extends Number & Comparable<T>> extends ArrayList<T> implements Supplier<List<? extends T>> {
    List<? extends T> get() { this }
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
        def closure = hierarchy.getTypeClosure(Argument.of(hierarchy.type, Integer), resolver(classLoader))

        then:
        strings(closure) == ['Payload<Integer>', 'ArrayList<Integer>', 'Supplier<List<? extends Integer>>', 'Object']
        size(classLoader, TypeHierarchies.recordClassName('test.Payload', 'test')) < 2048
    }

    void "diamond interfaces are deduplicated and a requested JDK class is recorded"() {
        given:
        def classLoader = buildClassLoader('''
package test

import io.micronaut.core.annotation.RecordTypeHierarchy

interface Top<T> {}
interface Left<T> extends Top<T> {}
interface Right<T> extends Top<T> {}

@RecordTypeHierarchy(classes = [Top, Left, Right, Diamond, ArrayList, AbstractList, List, Collection, Iterable, AbstractCollection])
class Diamond extends ArrayList<Integer> implements Left<String>, Right<String> {
}
''')
        def diamond = record(classLoader, 'test.Diamond')
        def arrayList = record(classLoader, 'java.util.ArrayList', 'test')

        expect:
        diamond.typeVariables.empty
        string(diamond.genericSuperclass.get()) == 'ArrayList<Integer>'
        strings(diamond.genericInterfaces) == ['Left<String>', 'Right<String>']
        arrayList.type == ArrayList
        arrayList.typeVariables*.variableName == ['E']
        string(arrayList.genericSuperclass.get()) == 'AbstractList<E>'
        strings(arrayList.genericInterfaces) == ['List<E>', 'RandomAccess', 'Cloneable', 'Serializable']

        when:
        def closure = diamond.getTypeClosure(Argument.of(diamond.type), resolver(classLoader))

        then: "the interfaces List adds in later JDKs aside"
        strings(closure) - ['SequencedCollection<Integer>'] == ['Diamond', 'ArrayList<Integer>', 'AbstractList<Integer>', 'AbstractCollection<Integer>', 'Collection<Integer>', 'Iterable<Integer>',
                             'List<Integer>', 'RandomAccess', 'Cloneable', 'Serializable', 'Left<String>', 'Top<String>', 'Right<String>', 'Object']
        size(classLoader, TypeHierarchies.recordClassName('java.util.ArrayList', 'test')) < 2048
    }

    void "a raw subtype erases the supertypes"() {
        given:
        def classLoader = buildClassLoader('''
package test

import io.micronaut.core.annotation.RecordTypeHierarchy

@RecordTypeHierarchy(classes = [Raw, ArrayList, AbstractList])
class Raw extends ArrayList implements Comparable {
    int compareTo(Object o) { 0 }
}
''')
        def raw = record(classLoader, 'test.Raw')

        expect:
        raw.genericSuperclass.get().isRawType()
        string(raw.genericSuperclass.get()) == 'ArrayList'
        raw.genericInterfaces[0].isRawType()

        when:
        def closure = raw.getTypeClosure(Argument.of(raw.type), resolver(classLoader))

        then:
        strings(closure) == ['Raw', 'ArrayList', 'AbstractList', 'AbstractCollection', 'List', 'RandomAccess', 'Cloneable', 'Serializable', 'Comparable', 'Object']
        closure[1].isRawType()
        closure[2].isRawType()
        closure[4].isRawType()
        !closure[5].isRawType()
    }

    void "a generic array argument"() {
        given:
        def classLoader = buildClassLoader('''
package test

import io.micronaut.core.annotation.RecordTypeHierarchy
import java.util.function.Supplier

@RecordTypeHierarchy
class Payload<T> implements Supplier<T[]> {
    T[] get() { null }
}
''')
        def hierarchy = record(classLoader, 'test.Payload')

        expect:
        strings(hierarchy.genericInterfaces) == ['Supplier<T[]>']
        hierarchy.genericInterfaces[0].typeParameters[0].type == Object[]
        hierarchy.genericInterfaces[0].typeParameters[0].isTypeVariable()

        when:
        def closure = hierarchy.getTypeClosure(Argument.of(hierarchy.type, String), resolver(classLoader))

        then:
        strings(closure) == ['Payload<String>', 'Supplier<String[]>', 'Object']
        closure[1].typeParameters[0].type == String[]
    }

    void "a record of a type with a package-private supertype is written into the package of the type"() {
        given:
        def classLoader = buildClassLoader('''
package test

import io.micronaut.core.annotation.RecordTypeHierarchy
import groovy.transform.PackageScope

@PackageScope
class Base<T> implements java.util.function.Supplier<T> {
    T get() { null }
}

class Sub extends Base<String> {
}

@RecordTypeHierarchy(classes = [Sub])
class Holder {
}
''')

        expect: "the record is written; the test class loader keeps the compiled classes in a loader of their own, so it is not instantiated here"
        ((Map<String, byte[]>) classLoader.generatedClasses).containsKey(TypeHierarchies.recordClassName('test.Sub', 'test'))
    }

    void "the same type is recorded once"() {
        given:
        def classLoader = buildClassLoader('''
package test

import io.micronaut.core.annotation.RecordTypeHierarchy

@RecordTypeHierarchy(classes = [ArrayList, ArrayList])
class One {
}

@RecordTypeHierarchy(classes = [ArrayList])
class Two {
}
''')

        expect:
        record(classLoader, 'java.util.ArrayList', 'test').type == ArrayList
    }
}
