package io.micronaut.core.type.hierarchy

import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.core.type.TypeHierarchies
import io.micronaut.core.type.TypeHierarchy
import io.micronaut.core.type.hierarchy.$$java.util.$ArrayList$TypeHierarchy
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Function

import static io.micronaut.core.type.hierarchy.Records.*
import static io.micronaut.core.type.hierarchy.TypeStrings.string
import static io.micronaut.core.type.hierarchy.TypeStrings.strings

class TypeHierarchySpec extends Specification {

    @TempDir
    Path tempDir

    static final Map<Class<?>, TypeHierarchy> KNOWN = [PAYLOAD, ARRAY_LIST, ABSTRACT_LIST, LIST, COLLECTION, TOP, LEFT, RIGHT, DIAMOND, ARRAYS, RAW_SUB]
        .collectEntries { [(it.type): it] }
    static final Function<Class<?>, Optional<TypeHierarchy>> RESOLVER = { Class<?> type -> Optional.ofNullable(KNOWN[type]) }
    static final Function<Class<?>, Optional<TypeHierarchy>> NONE = { Class<?> type -> Optional.empty() }

    void "the declaration is the type parameterized by its variables"() {
        expect:
        string(PAYLOAD.declaration) == 'Payload<T>'
        PAYLOAD.declaration.typeParameters[0] instanceof GenericPlaceholder
        PAYLOAD.declaration.typeParameters[0].bounds*.type == [Number]
        string(DIAMOND.declaration) == 'Diamond'
    }

    void "a parameterization substitutes the variables along the path, Object last"() {
        when:
        def closure = PAYLOAD.getTypeClosure(Argument.of(Payload, Integer), RESOLVER)

        then:
        strings(closure) == ['Payload<Integer>', 'ArrayList<Integer>', 'AbstractList<Integer>', 'AbstractCollection<Integer>', 'List<Integer>',
                             'Collection<Integer>', 'Iterable<Integer>', 'RandomAccess', 'Cloneable', 'Serializable', 'Supplier<List<Integer>>', 'Object']
        closure.last().is(Argument.OBJECT_ARGUMENT)
    }

    void "a wildcard argument stays a wildcard"() {
        when:
        def closure = PAYLOAD.getTypeClosure(of(Payload, extendsWildcard(Number)), RESOLVER)

        then:
        strings(closure).take(3) == ['Payload<? extends Number>', 'ArrayList<? extends Number>', 'AbstractList<? extends Number>']
        closure[1].typeParameters[0].isWildcard()
    }

    void "a diamond is deduplicated"() {
        when:
        def closure = DIAMOND.getTypeClosure(Argument.of(Diamond), RESOLVER)

        then:
        strings(closure) == ['Diamond', 'Left<String>', 'Top<String>', 'Right<String>', 'Object']
    }

    void "a raw parameterization erases the supertypes"() {
        when:
        def closure = PAYLOAD.getTypeClosure(Argument.of(Payload), RESOLVER)

        then:
        strings(closure) == ['Payload', 'ArrayList', 'AbstractList', 'AbstractCollection', 'List', 'Collection', 'Iterable', 'RandomAccess', 'Cloneable', 'Serializable', 'Supplier', 'Object']
        closure[1].isRawType()
        closure[4].isRawType()
        !closure[7].isRawType()
    }

    void "a raw extends erases the supertypes of the supertype"() {
        when:
        def closure = RAW_SUB.getTypeClosure(Argument.of(RawSub), RESOLVER)

        then:
        strings(closure) == ['RawSub', 'ArrayList', 'AbstractList', 'AbstractCollection', 'List', 'Collection', 'Iterable', 'RandomAccess', 'Cloneable', 'Serializable', 'Object']
        closure[1].isRawType()
        closure[4].isRawType()
    }

    void "an array of a variable becomes an array of the argument"() {
        when:
        def closure = ARRAYS.getTypeClosure(Argument.of(Arrays, String), RESOLVER)

        then:
        strings(closure) == ['Arrays<String>', 'Supplier<String[]>', 'Object']
        closure[1].typeParameters[0].type == String[]

        when:
        closure = ARRAYS.getTypeClosure(of(Arrays, extendsWildcard(Number)), RESOLVER)

        then:
        strings(closure) == ['Arrays<? extends Number>', 'Supplier<? extends Number[]>', 'Object']
    }

    void "a supertype without a record ends its branch"() {
        when:
        def closure = PAYLOAD.getTypeClosure(Argument.of(Payload, Integer), NONE)

        then:
        strings(closure) == ['Payload<Integer>', 'ArrayList<Integer>', 'Supplier<List<Integer>>', 'Object']
    }

    void "a parameterization of another type is refused"() {
        when:
        PAYLOAD.getTypeClosure(Argument.of(ArrayList, Integer), RESOLVER)

        then:
        thrown(IllegalArgumentException)
    }

    void "record names round trip"() {
        expect:
        TypeHierarchies.recordClassName(typeName, recordPackage) == recordName
        TypeHierarchies.recordedTypeName(recordName) == typeName

        where:
        typeName                       | recordPackage     | recordName
        'com.example.Foo'              | 'com.example'     | 'com.example.$Foo$TypeHierarchy'
        'com.example.Outer$Inner'      | 'com.example'     | 'com.example.$Outer$Inner$TypeHierarchy'
        'com.example.$Odd'             | 'com.example'     | 'com.example.$$Odd$TypeHierarchy'
        'java.util.ArrayList'          | 'com.example'     | 'com.example.$$java.util.$ArrayList$TypeHierarchy'
        'java.util.Map$Entry'          | 'com.example'     | 'com.example.$$java.util.$Map$Entry$TypeHierarchy'
        'a._Foo'                       | 'other'           | 'other.$$a.$_Foo$TypeHierarchy'
        'a_.Foo'                       | 'other'           | 'other.$$a_.$Foo$TypeHierarchy'
        'Foo'                          | ''                | '$Foo$TypeHierarchy'
        'Foo'                          | 'com.example'     | '$Foo$TypeHierarchy'
        'com.example.Foo'              | ''                | '$$com.example.$Foo$TypeHierarchy'
    }

    void "names that are not records are not decoded"() {
        expect:
        TypeHierarchies.recordedTypeName(name) == null

        where:
        name << ['com.example.Foo', 'com.example.Foo$TypeHierarchy', 'com.example.$TypeHierarchy', 'com.example.$$.$Foo$TypeHierarchy']
    }

    void "a lookup loads the record of the type asked for and no other"() {
        given:
        def serviceDir = tempDir.resolve("META-INF/micronaut/" + TypeHierarchy.name)
        Files.createDirectories(serviceDir)
        [$Records$Payload$TypeHierarchy, $Records$Unrelated$TypeHierarchy, $ArrayList$TypeHierarchy].each { Files.createFile(serviceDir.resolve(it.name)) }
        def classLoader = new URLClassLoader([tempDir.toUri().toURL()] as URL[], getClass().classLoader)
        TypeHierarchies.invalidate()

        when:
        def payload = TypeHierarchies.find(Payload.name, classLoader)

        then:
        payload.present
        payload.get().type == Payload
        payload.get().typeVariables*.name == ['T']
        string(payload.get().genericSuperclass.get()) == 'ArrayList<T>'
        $Records$Payload$TypeHierarchy.LOADED.get() == 1
        $Records$Unrelated$TypeHierarchy.LOADED.get() == 0

        when: "a JDK class recorded into another package"
        def arrayList = TypeHierarchies.find(ArrayList.name, classLoader)

        then:
        arrayList.present
        strings(arrayList.get().genericInterfaces) == ['List<E>', 'RandomAccess', 'Cloneable', 'Serializable']
        $Records$Unrelated$TypeHierarchy.LOADED.get() == 0

        when: "the closure resolves the supertypes through the records"
        def closure = payload.get().getTypeClosure(Argument.of(Payload, Long), { Class<?> type -> TypeHierarchies.find(type.name, classLoader) } as Function)

        then:
        strings(closure) == ['Payload<Long>', 'ArrayList<Long>', 'AbstractList<Long>', 'List<Long>', 'RandomAccess', 'Cloneable', 'Serializable', 'Supplier<List<Long>>', 'Object']

        and: "a type without a record"
        TypeHierarchies.find(Diamond.name, classLoader).empty
        TypeHierarchies.find(Diamond).empty

        cleanup:
        TypeHierarchies.invalidate()
    }
}
