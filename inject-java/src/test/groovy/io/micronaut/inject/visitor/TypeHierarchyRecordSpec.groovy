package io.micronaut.inject.visitor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.core.type.TypeHierarchies
import io.micronaut.core.type.TypeHierarchy
import io.micronaut.inject.ast.ClassElement

import java.util.function.Function

import static io.micronaut.inject.visitor.TypeStrings.string
import static io.micronaut.inject.visitor.TypeStrings.strings

class TypeHierarchyRecordSpec extends AbstractTypeElementSpec {

    static TypeHierarchy record(ClassLoader classLoader, String typeName, String recordPackage = typeName.substring(0, typeName.lastIndexOf('.'))) {
        def recordName = TypeHierarchies.recordClassName(typeName, recordPackage)
        assert classLoader.getResources("META-INF/micronaut/" + TypeHierarchy.name + "/" + recordName).hasMoreElements()
        (TypeHierarchy) classLoader.loadClass(recordName).getConstructor().newInstance()
    }

    static int size(ClassLoader classLoader, String recordName) {
        classLoader.getResources(recordName.replace('.', '/') + ".class").nextElement().openStream().bytes.length
    }

    static Function<Class<?>, Optional<TypeHierarchy>> resolver(ClassLoader classLoader) {
        return { Class<?> type ->
            def recordName = TypeHierarchies.recordClassName(type.name, 'test')
            try {
                Optional.of((TypeHierarchy) classLoader.loadClass(recordName).getConstructor().newInstance())
            } catch (ClassNotFoundException e) {
                Optional.empty()
            }
        } as Function
    }

    void "a generic class with a bounded variable"() {
        given:
        def classLoader = buildClassLoader('test.Payload', '''
package test;

import io.micronaut.core.annotation.RecordTypeHierarchy;
import java.util.*;
import java.util.function.Supplier;

@RecordTypeHierarchy
public class Payload<T extends Number & Comparable<T>> extends ArrayList<T> implements Supplier<List<? extends T>> {
    public List<? extends T> get() { return this; }
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

        then: "the supertypes without a record end the walk"
        strings(closure) == ['Payload<Integer>', 'ArrayList<Integer>', 'Supplier<List<? extends Integer>>', 'Object']
        println('record bytes test.Payload: ' + size(classLoader, TypeHierarchies.recordClassName('test.Payload', 'test')))
        size(classLoader, TypeHierarchies.recordClassName('test.Payload', 'test')) < 2048
    }

    void "diamond interfaces are deduplicated and a requested JDK class is recorded"() {
        given:
        def classLoader = buildClassLoader('test.Diamond', '''
package test;

import io.micronaut.core.annotation.RecordTypeHierarchy;
import java.util.*;

interface Top<T> {}
interface Left<T> extends Top<T> {}
interface Right<T> extends Top<T> {}

@RecordTypeHierarchy(classes = {Top.class, Left.class, Right.class, Diamond.class, ArrayList.class, AbstractList.class, List.class, Collection.class, Iterable.class, AbstractCollection.class})
public class Diamond extends ArrayList<Integer> implements Left<String>, Right<String> {
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
        println('record bytes java.util.ArrayList: ' + size(classLoader, TypeHierarchies.recordClassName('java.util.ArrayList', 'test')) + ', test.Diamond: ' + size(classLoader, TypeHierarchies.recordClassName('test.Diamond', 'test')) + ', test.Top: ' + size(classLoader, TypeHierarchies.recordClassName('test.Top', 'test')))
        size(classLoader, TypeHierarchies.recordClassName('java.util.ArrayList', 'test')) < 2048
    }

    void "a raw subtype erases the supertypes"() {
        given:
        def classLoader = buildClassLoader('test.Raw', '''
package test;

import io.micronaut.core.annotation.RecordTypeHierarchy;
import java.util.*;

@RecordTypeHierarchy(classes = {Raw.class, ArrayList.class, AbstractList.class})
public class Raw extends ArrayList implements Comparable {
    public int compareTo(Object o) { return 0; }
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
        def classLoader = buildClassLoader('test.Payload', '''
package test;

import io.micronaut.core.annotation.RecordTypeHierarchy;
import java.util.function.Supplier;

@RecordTypeHierarchy
public class Payload<T> implements Supplier<T[]> {
    public T[] get() { return null; }
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
        def classLoader = buildClassLoader(new JavaFiles()
            .add('other.Base', '''
package other;

class Base<T> implements java.util.function.Supplier<T> {
    public T get() { return null; }
}
''')
            .add('other.Sub', '''
package other;

public class Sub extends Base<String> {
}
''')
            .add('test.Holder', '''
package test;

import io.micronaut.core.annotation.RecordTypeHierarchy;

@RecordTypeHierarchy(classes = {other.Sub.class, java.util.ArrayList.class})
public class Holder {
}
'''))
        def sub = record(classLoader, 'other.Sub')

        expect: "the record of the public JDK class stays with the holder"
        classLoader.loadClass(TypeHierarchies.recordClassName('java.util.ArrayList', 'test'))
        string(sub.genericSuperclass.get()) == 'Base<String>'
        sub.genericSuperclass.get().type == classLoader.loadClass('other.Base')
    }

    void "the same type is recorded once"() {
        given:
        def classLoader = buildClassLoader('test.One', '''
package test;

import io.micronaut.core.annotation.RecordTypeHierarchy;

@RecordTypeHierarchy(classes = {java.util.ArrayList.class, java.util.ArrayList.class})
class One {
}

@RecordTypeHierarchy(classes = java.util.ArrayList.class)
class Two {
}
''')

        expect:
        record(classLoader, 'java.util.ArrayList', 'test').type == ArrayList
    }

    void "a parameterized usage records the declaration"() {
        given: "a visitor that records the generic type of each field of FieldHolder"
        def classLoader = buildClassLoader('test.FieldHolder', '''
package test;

import java.util.function.Supplier;

class Box<T> implements Supplier<T> {
    public T get() { return null; }
}

class FieldHolder {
    Box<String> strings;
}
''')
        def box = record(classLoader, 'test.Box')

        expect: "the record is of Box<T>, not of the Box<String> that was passed"
        string(box.declaration) == 'Box<T>'
        strings(box.genericInterfaces) == ['Supplier<T>']

        when:
        def closure = box.getTypeClosure(Argument.of(box.type, Integer), resolver(classLoader))

        then:
        strings(closure) == ['Box<Integer>', 'Supplier<Integer>', 'Object']
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        // local visitors replace the ones found as services: the annotation visitor is kept
        return [new TypeHierarchyVisitor(), new FieldTypeRecordingVisitor()]
    }

    static class FieldTypeRecordingVisitor implements TypeElementVisitor<Object, Object> {

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.simpleName == 'FieldHolder') {
                element.fields.each { field -> context.recordTypeHierarchy(field.genericType, element) }
            }
        }

        @Override
        TypeElementVisitor.VisitorKind getVisitorKind() {
            return TypeElementVisitor.VisitorKind.ISOLATING
        }
    }

    protected ClassLoader buildClassLoader(JavaFiles files) {
        try (def parser = newJavaParser()) {
            def javaFiles = parser.generate(files.files.stream()
                .map { com.google.testing.compile.JavaFileObjects.forSourceString(it.key, it.value) }
                .toArray(javax.tools.JavaFileObject[]::new))
            return new io.micronaut.annotation.processing.test.JavaFileObjectClassLoader(javaFiles)
        }
    }
}
