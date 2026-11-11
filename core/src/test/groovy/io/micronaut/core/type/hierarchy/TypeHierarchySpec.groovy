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

import static io.micronaut.core.type.hierarchy.Records.*
import static io.micronaut.core.type.hierarchy.TypeStrings.string
import static io.micronaut.core.type.hierarchy.TypeStrings.strings

class TypeHierarchySpec extends Specification {

    @TempDir
    Path tempDir

    void "the declaration is the type parameterized by its variables"() {
        expect:
        string(PAYLOAD.declaration) == 'Payload<T>'
        PAYLOAD.declaration.typeParameters[0] instanceof GenericPlaceholder
        PAYLOAD.declaration.typeParameters[0].bounds*.type == [Number]
        string(DIAMOND.declaration) == 'Diamond'
    }

    void "a parameterization substitutes the variables along the path, Object last"() {
        when:
        def closure = PAYLOAD.getTypeClosure(Argument.of(Payload, Integer))

        then:
        strings(closure) == ['Payload<Integer>', 'ArrayList<Integer>', 'AbstractList<Integer>', 'AbstractCollection<Integer>', 'Collection<Integer>',
                             'Iterable<Integer>', 'List<Integer>', 'RandomAccess', 'Cloneable', 'Serializable', 'Supplier<List<Integer>>', 'Object']
        closure.last().is(Argument.OBJECT_ARGUMENT)
    }

    void "a wildcard argument stays a wildcard"() {
        when:
        def closure = PAYLOAD.getTypeClosure(of(Payload, extendsWildcard(Number)))

        then:
        strings(closure).take(3) == ['Payload<? extends Number>', 'ArrayList<? extends Number>', 'AbstractList<? extends Number>']
        closure[1].typeParameters[0].isWildcard()
    }

    void "a diamond is deduplicated"() {
        when:
        def closure = DIAMOND.getTypeClosure(Argument.of(Diamond))

        then:
        strings(closure) == ['Diamond', 'Left<String>', 'Top<String>', 'Right<String>', 'Object']
    }

    void "a raw parameterization erases the supertypes"() {
        when:
        def closure = PAYLOAD.getTypeClosure(Argument.of(Payload))

        then:
        strings(closure) == ['Payload', 'ArrayList', 'AbstractList', 'AbstractCollection', 'Collection', 'Iterable', 'List', 'RandomAccess', 'Cloneable', 'Serializable', 'Supplier', 'Object']
        closure[1].isRawType()
        closure[4].isRawType()
        !closure[7].isRawType()
    }

    void "a raw extends erases the supertypes of the supertype"() {
        when:
        def closure = RAW_SUB.getTypeClosure(Argument.of(RawSub))

        then:
        strings(closure) == ['RawSub', 'ArrayList', 'AbstractList', 'AbstractCollection', 'Collection', 'Iterable', 'List', 'RandomAccess', 'Cloneable', 'Serializable', 'Object']
        closure[1].isRawType()
        closure[4].isRawType()
    }

    void "an array of a variable becomes an array of the argument"() {
        when:
        def closure = ARRAYS.getTypeClosure(Argument.of(Arrays, String))

        then:
        strings(closure) == ['Arrays<String>', 'Supplier<String[]>', 'Object']
        closure[1].typeParameters[0].type == String[]

        when:
        closure = ARRAYS.getTypeClosure(of(Arrays, extendsWildcard(Number)))

        then:
        strings(closure) == ['Arrays<? extends Number>', 'Supplier<? extends Number[]>', 'Object']
    }

    void "a parameterization of another type is refused"() {
        when:
        PAYLOAD.getTypeClosure(Argument.of(ArrayList, Integer))

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

    void "record names after the class that recorded the type round trip"() {
        expect:
        TypeHierarchies.recordClassName(typeName, recordPackage, origin) == recordName
        TypeHierarchies.recordedTypeName(recordName) == typeName

        where:
        typeName                       | recordPackage     | origin          | recordName
        'com.example.Foo'              | 'com.example'     | 'Holder'        | 'com.example.$Foo$TypeHierarchy$$Holder'
        'com.example.Foo'              | 'com.example'     | 'Foo'           | 'com.example.$Foo$TypeHierarchy$$Foo'
        'com.example.Outer$Inner'      | 'com.example'     | 'Outer'         | 'com.example.$Outer$Inner$TypeHierarchy$$Outer'
        'com.example.$Odd'             | 'com.example'     | 'Holder'        | 'com.example.$$Odd$TypeHierarchy$$Holder'
        'com.example.Foo'              | 'com.example'     | 'Odd$Holder'    | 'com.example.$Foo$TypeHierarchy$$Odd_Holder'
        'com.example.Foo'              | 'com.example'     | 'TypeHierarchy' | 'com.example.$Foo$TypeHierarchy$$TypeHierarchy'
        'java.util.ArrayList'          | 'com.example'     | 'Holder'        | 'com.example.$$java.util.$ArrayList$TypeHierarchy$$Holder'
        'java.util.Map$Entry'          | 'com.example'     | 'Holder'        | 'com.example.$$java.util.$Map$Entry$TypeHierarchy$$Holder'
        'Foo'                          | ''                | 'Holder'        | '$Foo$TypeHierarchy$$Holder'
        'com.example.Foo'              | ''                | 'Holder'        | '$$com.example.$Foo$TypeHierarchy$$Holder'
        'com.example.Foo'              | 'com.example'     | null            | 'com.example.$Foo$TypeHierarchy'
        'com.example.Foo'              | 'com.example'     | ''              | 'com.example.$Foo$TypeHierarchy'
    }

    void "names that are not records are not decoded"() {
        expect:
        TypeHierarchies.recordedTypeName(name) == null

        where:
        name << ['com.example.Foo', 'com.example.Foo$TypeHierarchy', 'com.example.$TypeHierarchy', 'com.example.$$.$Foo$TypeHierarchy',
                 'com.example.$Foo$TypeHierarchy$$', 'com.example.Foo$TypeHierarchy$$Holder', 'com.example.$TypeHierarchy$$Holder',
                 'com.example.$Foo$TypeHierarchy$Holder']
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

        when: "the closure is the recorded super types with the arguments substituted"
        def closure = payload.get().getTypeClosure(Argument.of(Payload, Long))

        then:
        strings(closure) == ['Payload<Long>', 'ArrayList<Long>', 'AbstractList<Long>', 'AbstractCollection<Long>', 'Collection<Long>', 'Iterable<Long>', 'List<Long>', 'RandomAccess', 'Cloneable', 'Serializable', 'Supplier<List<Long>>', 'Object']

        and: "a type without a record"
        TypeHierarchies.find(Diamond.name, classLoader).empty
        TypeHierarchies.find(Diamond).empty

        cleanup:
        TypeHierarchies.invalidate()
    }

    void "a record written without the names of what it left out has none"() {
        expect: "the constructor without the names, which carries no information on what was left out"
        new $Records$Payload$TypeHierarchy().omittedSuperTypeNames.empty
        new io.micronaut.core.type.$$java.util.$ArrayList$TypeHierarchy().omittedSuperTypeNames == ['java.util.AbstractList']
    }

    void "records named after the class that recorded them are found beside the older names, with the same preference (#order)"() {
        given: "a record of Payload in its package and an older one outside it, and two records of ArrayList outside its package that each left a super type out"
        def serviceDir = tempDir.resolve("META-INF/micronaut/" + TypeHierarchy.name)
        Files.createDirectories(serviceDir)
        def records = [
            'io.micronaut.core.type.$$io.micronaut.core.type.hierarchy.$Records$Payload$TypeHierarchy',
            'io.micronaut.core.type.hierarchy.$Records$Payload$TypeHierarchy$$Holder',
            'io.micronaut.core.type.$$java.util.$ArrayList$TypeHierarchy$$Second',
            'io.micronaut.core.type.$$java.util.$ArrayList$TypeHierarchy$$First'
        ]
        (order == 'forward' ? records : records.reverse()).each { Files.createFile(serviceDir.resolve(it)) }
        def classLoader = new URLClassLoader([tempDir.toUri().toURL()] as URL[], getClass().classLoader)
        TypeHierarchies.invalidate()

        expect: "the record in the package of the type, whatever its name"
        TypeHierarchies.find(Payload.name, classLoader).get().class.name == 'io.micronaut.core.type.hierarchy.$Records$Payload$TypeHierarchy$$Holder'

        and: "of records that left out as many super types, the first by name"
        TypeHierarchies.find(ArrayList.name, classLoader).get().class.name == 'io.micronaut.core.type.$$java.util.$ArrayList$TypeHierarchy$$First'

        when: "an older record that left nothing out is added"
        Files.createFile(serviceDir.resolve($ArrayList$TypeHierarchy.name))
        TypeHierarchies.invalidate()

        then: "it is preferred over the newer names that left a super type out"
        TypeHierarchies.find(ArrayList.name, classLoader).get().class == $ArrayList$TypeHierarchy

        cleanup:
        TypeHierarchies.invalidate()

        where:
        order << ['forward', 'reverse']
    }

    void "a type recorded from several packages is found in the record that names the most, whatever the order (#order)"() {
        given: "the records of Payload and ArrayList written from two packages each, listed in the given order"
        def serviceDir = tempDir.resolve("META-INF/micronaut/" + TypeHierarchy.name)
        Files.createDirectories(serviceDir)
        def records = [
            $Records$Payload$TypeHierarchy.name,
            'io.micronaut.core.type.$$io.micronaut.core.type.hierarchy.$Records$Payload$TypeHierarchy',
            $ArrayList$TypeHierarchy.name,
            'io.micronaut.core.type.$$java.util.$ArrayList$TypeHierarchy'
        ]
        (order == 'forward' ? records : records.reverse()).each { Files.createFile(serviceDir.resolve(it)) }
        def classLoader = new URLClassLoader([tempDir.toUri().toURL()] as URL[], getClass().classLoader)
        TypeHierarchies.invalidate()

        when:
        def payload = TypeHierarchies.find(Payload.name, classLoader).get()
        def arrayList = TypeHierarchies.find(ArrayList.name, classLoader).get()

        then: "the record in the package of the type, which sorts after the other"
        payload.class == $Records$Payload$TypeHierarchy
        string(payload.genericSuperclass.get()) == 'ArrayList<T>'

        and: "of two records written outside the package of the type, the one that left nothing out, which sorts after the other"
        arrayList.class == $ArrayList$TypeHierarchy
        arrayList.omittedSuperTypeNames.empty

        cleanup:
        TypeHierarchies.invalidate()

        where:
        order << ['forward', 'reverse']
    }
}
