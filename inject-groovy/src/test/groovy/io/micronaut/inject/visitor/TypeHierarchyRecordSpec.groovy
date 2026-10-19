package io.micronaut.inject.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.core.type.TypeHierarchies
import io.micronaut.core.type.TypeHierarchy
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.test.hierarchy.RecordingTypeHierarchyVisitor
import io.micronaut.inject.ast.ElementQuery


import static io.micronaut.inject.test.hierarchy.TypeStrings.string
import static io.micronaut.inject.test.hierarchy.TypeStrings.strings

class TypeHierarchyRecordSpec extends AbstractBeanDefinitionSpec {

    static TypeHierarchy record(ClassLoader classLoader, String typeName, String recordPackage = typeName.substring(0, typeName.lastIndexOf('.'))) {
        (TypeHierarchy) classLoader.loadClass(TypeHierarchies.recordClassName(typeName, recordPackage)).getConstructor().newInstance()
    }

    static int size(ClassLoader classLoader, String recordName) {
        ((Map<String, byte[]>) classLoader.generatedClasses)[recordName].length
    }

    void "a generic class with a bounded variable"() {
        given:
        def classLoader = buildClassLoader('''
package test

import io.micronaut.core.annotation.TypeHierarchyImport
import java.util.function.Supplier

@TypeHierarchyImport
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
        def closure = hierarchy.getTypeClosure(Argument.of(hierarchy.type, Integer))

        then:
        strings(closure) - ['SequencedCollection<Integer>'] == ['Payload<Integer>', 'ArrayList<Integer>', 'AbstractList<Integer>', 'AbstractCollection<Integer>', 'Collection<Integer>', 'Iterable<Integer>',
                             'List<Integer>', 'RandomAccess', 'Cloneable', 'Serializable', 'Supplier<List<? extends Integer>>', 'Object']
        size(classLoader, TypeHierarchies.recordClassName('test.Payload', 'test')) < 4096
    }

    void "diamond interfaces are deduplicated and a requested JDK class is recorded"() {
        given:
        def classLoader = buildClassLoader('''
package test

import io.micronaut.core.annotation.TypeHierarchyImport

interface Top<T> {}
interface Left<T> extends Top<T> {}
interface Right<T> extends Top<T> {}

@TypeHierarchyImport(classes = [Top, Left, Right, Diamond, ArrayList, AbstractList, List, Collection, Iterable, AbstractCollection])
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
        def closure = diamond.getTypeClosure(Argument.of(diamond.type))

        then: "the interfaces List adds in later JDKs aside"
        strings(closure) - ['SequencedCollection<Integer>'] == ['Diamond', 'ArrayList<Integer>', 'AbstractList<Integer>', 'AbstractCollection<Integer>', 'Collection<Integer>', 'Iterable<Integer>',
                             'List<Integer>', 'RandomAccess', 'Cloneable', 'Serializable', 'Left<String>', 'Top<String>', 'Right<String>', 'Object']
        size(classLoader, TypeHierarchies.recordClassName('java.util.ArrayList', 'test')) < 4096
    }

    void "a raw subtype erases the supertypes"() {
        given:
        def classLoader = buildClassLoader('''
package test

import io.micronaut.core.annotation.TypeHierarchyImport

@TypeHierarchyImport(classes = [Raw, ArrayList, AbstractList])
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
        def closure = raw.getTypeClosure(Argument.of(raw.type))

        then:
        strings(closure) - ['SequencedCollection'] == ['Raw', 'ArrayList', 'AbstractList', 'AbstractCollection', 'Collection', 'Iterable', 'List', 'RandomAccess', 'Cloneable', 'Serializable', 'Comparable', 'Object']
        closure[1].isRawType()
        closure[2].isRawType()
        closure[6].isRawType()
        !closure[closure.size() - 5].isRawType()
    }

    void "a generic array argument"() {
        given:
        def classLoader = buildClassLoader('''
package test

import io.micronaut.core.annotation.TypeHierarchyImport
import java.util.function.Supplier

@TypeHierarchyImport
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
        def closure = hierarchy.getTypeClosure(Argument.of(hierarchy.type, String))

        then:
        strings(closure) == ['Payload<String>', 'Supplier<String[]>', 'Object']
        closure[1].typeParameters[0].type == String[]
    }

    void "a multidimensional array has the super types of the arrays it is an array of, and a record argument keeps its dimensions"() {
        given:
        def source = '''
package test

import io.micronaut.core.annotation.TypeHierarchyImport
import java.util.function.Function

@TypeHierarchyImport
abstract class Matrix<T> implements Function<T[][], int[][]> {
    String string
    String[][] strings
    int[][] ints
}
'''
        ClassElement matrix = buildClassElement('test.Matrix', source)
        def string = elementStrings(fieldType(matrix, 'string'))
        def hierarchy = record(buildClassLoader(source), 'test.Matrix')

        expect: "String[][] is an array of the super types of String, then of those String[] has as an array"
        string.last() == 'Object'
        string.contains('Comparable<String>')
        elementStrings(fieldType(matrix, 'strings')) == ['String[][]'] + string.tail().collect { it + '[][]' } +
            ['Object[]', 'Cloneable[]', 'Serializable[]', 'Cloneable', 'Serializable', 'Object']

        and: "int[][] is an array of the super types of int[]"
        elementStrings(fieldType(matrix, 'ints')) == ['int[][]', 'Object[]', 'Cloneable[]', 'Serializable[]', 'Cloneable', 'Serializable', 'Object']

        when:
        def closure = hierarchy.getTypeClosure(Argument.of(hierarchy.type, String))
        def ofArrays = hierarchy.getTypeClosure(Argument.of(hierarchy.type, String[]))

        then:
        strings(hierarchy.genericInterfaces) == ['Function<T[][], int[][]>']
        strings(closure) == ['Matrix<String>', 'Function<String[][], int[][]>', 'Object']
        closure[1].typeParameters*.type == [String[][], int[][]]
        strings(ofArrays) == ['Matrix<String[]>', 'Function<String[][][], int[][]>', 'Object']
        ofArrays[1].typeParameters*.type == [String[][][], int[][]]
    }

    private static ClassElement fieldType(ClassElement owner, String name) {
        owner.getEnclosedElements(ElementQuery.ALL_FIELDS).find { it.name == name }.genericType
    }

    /**
     * The super types of an element by simple name, arguments and dimensions, as the strings of a record read.
     */
    private static List<String> elementStrings(ClassElement type) {
        type.getAllSuperTypes().collect { elementString(it) }
    }

    private static String elementString(ClassElement type) {
        String name = type.name.substring(type.name.lastIndexOf('.') + 1)
        String dimensions = "[]" * type.arrayDimensions
        if (type.rawType || type.typeArguments.isEmpty()) {
            return name + dimensions
        }
        return name + "<" + type.typeArguments.values().collect { elementString(it) }.join(", ") + ">" + dimensions
    }

    void "wildcards of every kind are kept through the substitution and erased for a raw parameterization"() {
        given:
        def classLoader = buildClassLoader('''
package test

import io.micronaut.core.annotation.TypeHierarchyImport
import java.util.*
import java.util.function.*

@TypeHierarchyImport
class Wild<T> implements Supplier<List<? extends T>>, Consumer<List<? super T>>, Function<List<?>, Map<? extends Number, ? super T>> {
    public List<? extends T> get() { return null; }
    void accept(List<? super T> list) { }
    public Map<? extends Number, ? super T> apply(List<?> list) { return null; }
}
''')
        def hierarchy = record(classLoader, 'test.Wild')

        expect:
        strings(hierarchy.superTypes) == ['Supplier<List<? extends T>>', 'Consumer<List<? super T>>', 'Function<List<?>, Map<? extends Number, ? super T>>', 'Object']
        hierarchy.superTypes[0].typeParameters[0].typeParameters[0].isWildcard()
        hierarchy.superTypes[2].typeParameters[0].typeParameters[0].isWildcard()

        when:
        def closure = hierarchy.getTypeClosure(Argument.of(hierarchy.type, Integer))

        then:
        strings(closure) == ['Wild<Integer>', 'Supplier<List<? extends Integer>>', 'Consumer<List<? super Integer>>', 'Function<List<?>, Map<? extends Number, ? super Integer>>', 'Object']
        closure[1].typeParameters[0].typeParameters[0].isWildcard()
        closure[2].typeParameters[0].typeParameters[0].upperBounds*.type == [Object]
        closure[2].typeParameters[0].typeParameters[0].lowerBounds*.type == [Integer]

        when:
        closure = hierarchy.getTypeClosure(Argument.of(hierarchy.type))

        then:
        strings(closure) == ['Wild', 'Supplier', 'Consumer', 'Function', 'Object']
        closure[1].isRawType() && closure[3].isRawType()
    }

    void "variables are swapped, nested and made arrays along the path"() {
        given:
        def classLoader = buildClassLoader('''
package test

import io.micronaut.core.annotation.TypeHierarchyImport
import java.util.*

@TypeHierarchyImport(classes = [Swapped, Nested])
class Swapped<K, V> extends HashMap<V, K> {
}

class Nested<T> extends ArrayList<List<T[]>> implements Comparable<Nested<T>> {
    public int compareTo(Nested<T> other) { return 0; }
}
''')
        def swapped = record(classLoader, 'test.Swapped')
        def nested = record(classLoader, 'test.Nested')

        expect:
        strings(swapped.superTypes) == ['HashMap<V, K>', 'AbstractMap<V, K>', 'Map<V, K>', 'Cloneable', 'Serializable', 'Object']
        strings(swapped.getTypeClosure(Argument.of(swapped.type, String, Integer))) == ['Swapped<String, Integer>', 'HashMap<Integer, String>', 'AbstractMap<Integer, String>', 'Map<Integer, String>', 'Cloneable', 'Serializable', 'Object']
        strings(nested.getTypeClosure(Argument.of(nested.type, String))) - ['SequencedCollection<List<String[]>>'] == ['Nested<String>', 'ArrayList<List<String[]>>', 'AbstractList<List<String[]>>', 'AbstractCollection<List<String[]>>',
                                                                                                                       'Collection<List<String[]>>', 'Iterable<List<String[]>>', 'List<List<String[]>>', 'RandomAccess', 'Cloneable', 'Serializable', 'Comparable<Nested<String>>', 'Object']
        nested.getTypeClosure(Argument.of(nested.type, String))[1].typeParameters[0].typeParameters[0].type == String[]
    }

    void "recursive and intersection bounds are recorded on the variables"() {
        given:
        def classLoader = buildClassLoader('''
package test

import io.micronaut.core.annotation.TypeHierarchyImport
import java.io.Serializable
import java.util.*
import java.util.function.Supplier

@TypeHierarchyImport
class Ordered<T extends Comparable<T> & Serializable, U extends List<T>> implements Supplier<U> {
    public U get() { return null; }
}
''')
        def hierarchy = record(classLoader, 'test.Ordered')

        expect:
        string(hierarchy.declaration) == 'Ordered<T, U>'
        hierarchy.typeVariables*.variableName == ['T', 'U']
        strings(hierarchy.typeVariables[0].bounds) == ['Comparable<T>', 'Serializable']
        hierarchy.typeVariables[0].type == Comparable
        strings(hierarchy.typeVariables[1].bounds) == ['List<T>']
        hierarchy.typeVariables[1].type == List
        strings(hierarchy.getTypeClosure(Argument.of(hierarchy.type, Argument.of(String), Argument.of(ArrayList, String)))) == ['Ordered<String, ArrayList<String>>', 'Supplier<ArrayList<String>>', 'Object']
    }

    void "a record of a type with a package-private supertype is written into the package of the type"() {
        given:
        def classLoader = buildClassLoader('''
package test

import io.micronaut.core.annotation.TypeHierarchyImport
import groovy.transform.PackageScope

@PackageScope
class Base<T> implements java.util.function.Supplier<T> {
    T get() { null }
}

class Sub extends Base<String> {
}

@TypeHierarchyImport(classes = [Sub])
class Holder {
}
''')

        expect: "the record is written; the test class loader keeps the compiled classes in a loader of their own, so it is not instantiated here"
        ((Map<String, byte[]>) classLoader.generatedClasses).containsKey(TypeHierarchies.recordClassName('test.Sub', 'test'))
    }

    void "recording a type reports the super types the record leaves out, as the record lists them"() {
        given: "a visitor that records the imported classes a second time and keeps what the API returned"
        def classLoader = buildClassLoader('''
package probe

import io.micronaut.core.annotation.TypeHierarchyImport

@TypeHierarchyImport(classes = [ArrayList, StringBuilder, java.util.jar.JarEntry])
class Reported {
}
''')
        def arrayList = RecordingTypeHierarchyVisitor.RECORDS[TypeHierarchies.recordClassName('java.util.ArrayList', 'probe')]
        def stringBuilder = RecordingTypeHierarchyVisitor.RECORDS[TypeHierarchies.recordClassName('java.lang.StringBuilder', 'probe')]
        def jarEntry = RecordingTypeHierarchyVisitor.RECORDS[TypeHierarchies.recordClassName('java.util.jar.JarEntry', 'probe')]

        expect: "a class whose closure the record names in full"
        arrayList.className == TypeHierarchies.recordClassName('java.util.ArrayList', 'probe')
        arrayList.omittedSuperTypeNames == []
        arrayList.complete
        record(classLoader, 'java.util.ArrayList', 'probe').omittedSuperTypeNames == []

        and: "a class with a package-private superclass"
        stringBuilder.className == TypeHierarchies.recordClassName('java.lang.StringBuilder', 'probe')
        stringBuilder.omittedSuperTypeNames == ['java.lang.AbstractStringBuilder']
        !stringBuilder.complete
        stringBuilder.omittedSuperTypeNames == record(classLoader, 'java.lang.StringBuilder', 'probe').omittedSuperTypeNames

        and: "a class with a package-private ancestor of a third package"
        jarEntry.omittedSuperTypeNames == ['java.util.zip.ZipConstants']
        !jarEntry.complete
        jarEntry.omittedSuperTypeNames == record(classLoader, 'java.util.jar.JarEntry', 'probe').omittedSuperTypeNames
    }

    void "a JDK class with a package-private ancestor is recorded in the package of the origin"() {
        given:
        def classLoader = buildClassLoader('''
package probe

import io.micronaut.core.annotation.TypeHierarchyImport

@TypeHierarchyImport(classes = [StringBuilder, ArrayList, java.util.jar.JarEntry])
class Hierarchies {
}
''')
        def stringBuilder = record(classLoader, 'java.lang.StringBuilder', 'probe')
        def jarEntry = record(classLoader, 'java.util.jar.JarEntry', 'probe')

        expect: "AbstractStringBuilder is left out by name, the interfaces above it are kept"
        record(classLoader, 'java.util.ArrayList', 'probe').type == ArrayList
        stringBuilder.type == StringBuilder
        stringBuilder.genericSuperclass.empty
        stringBuilder.omittedSuperTypeNames == ['java.lang.AbstractStringBuilder']
        !strings(stringBuilder.superTypes).contains('AbstractStringBuilder')
        strings(stringBuilder.superTypes).containsAll(['Appendable', 'CharSequence', 'Comparable<StringBuilder>', 'Serializable'])
        strings(stringBuilder.superTypes).last() == 'Object'

        and: "an ancestor of a third package is left out the same way"
        jarEntry.omittedSuperTypeNames == ['java.util.zip.ZipConstants']
        strings(jarEntry.superTypes) == ['ZipEntry', 'Cloneable', 'Object']
    }

    void "the same type is recorded once"() {
        given:
        def classLoader = buildClassLoader('''
package test

import io.micronaut.core.annotation.TypeHierarchyImport

@TypeHierarchyImport(classes = [ArrayList, ArrayList])
class One {
}

@TypeHierarchyImport(classes = [ArrayList])
class Two {
}
''')

        expect:
        record(classLoader, 'java.util.ArrayList', 'test').type == ArrayList
    }
}
