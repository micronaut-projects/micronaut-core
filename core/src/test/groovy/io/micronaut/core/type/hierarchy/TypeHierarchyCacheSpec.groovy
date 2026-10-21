package io.micronaut.core.type.hierarchy

import io.micronaut.core.type.TypeHierarchies
import io.micronaut.core.type.TypeHierarchy
import io.micronaut.core.type.hierarchy.$$java.util.$ArrayList$TypeHierarchy
import spock.lang.Specification
import spock.lang.TempDir

import java.lang.ref.WeakReference
import java.nio.file.Files
import java.nio.file.Path

import static io.micronaut.core.type.hierarchy.Records.Payload

class TypeHierarchyCacheSpec extends Specification {

    private static final String OTHER_ARRAY_LIST_RECORD = 'io.micronaut.core.type.$$java.util.$ArrayList$TypeHierarchy'

    @TempDir
    Path tempDir

    void setup() {
        TypeHierarchies.invalidate()
    }

    void cleanup() {
        TypeHierarchies.invalidate()
    }

    void "a record is found once per class loader"() {
        given: "a loader that defines its own record, which counts how often it is instantiated"
        def classLoader = new RecordDefiningClassLoader(services('payload', $Records$Payload$TypeHierarchy.name), getClass().classLoader)

        when:
        def first = TypeHierarchies.find(Payload.name, classLoader).get()
        def second = TypeHierarchies.find(Payload.name, classLoader).get()

        then:
        first.class.classLoader.is(classLoader)
        first.is(second)
        first.class.LOADED.get() == 1

        when: "the records are invalidated"
        TypeHierarchies.invalidate()
        def third = TypeHierarchies.find(Payload.name, classLoader).get()

        then:
        !third.is(first)
        third.class.is(first.class)
        TypeHierarchies.find(Payload.name, classLoader).get().is(third)
        first.class.LOADED.get() == 2
    }

    void "two child loaders with their own records of one type each find their own"() {
        given: "two loaders that define a record of ArrayList each, written from two packages"
        def first = new RecordDefiningClassLoader(services('first', $ArrayList$TypeHierarchy.name), getClass().classLoader)
        def second = new RecordDefiningClassLoader(services('second', OTHER_ARRAY_LIST_RECORD), getClass().classLoader)

        when:
        def fromFirst = TypeHierarchies.find(ArrayList.name, first).get()
        def fromSecond = TypeHierarchies.find(ArrayList.name, second).get()

        then:
        fromFirst.type == ArrayList
        fromFirst.class.name == $ArrayList$TypeHierarchy.name
        fromFirst.class.classLoader.is(first)
        fromSecond.type == ArrayList
        fromSecond.class.name == OTHER_ARRAY_LIST_RECORD
        fromSecond.class.classLoader.is(second)
        fromSecond.omittedSuperTypeNames == ['java.util.AbstractList']

        and: "each is found once"
        TypeHierarchies.find(ArrayList.name, first).get().is(fromFirst)
        TypeHierarchies.find(ArrayList.name, second).get().is(fromSecond)
    }

    void "a child loader whose records were found can be collected"() {
        given: "a loader that defined the record found in it, and is no longer used"
        def loader = RecordDefiningClassLoader.findInDisposableLoader(services('disposable', $ArrayList$TypeHierarchy.name), getClass().classLoader, ArrayList.name)

        expect:
        collected(loader)
    }

    private static boolean collected(WeakReference<?> reference) {
        long deadline = System.nanoTime() + 10_000_000_000L
        while (reference.get() != null && System.nanoTime() < deadline) {
            System.gc()
            Thread.sleep(50)
        }
        return reference.get() == null
    }

    private Path services(String name, String... recordNames) {
        def root = tempDir.resolve(name)
        def serviceDir = root.resolve("META-INF/micronaut/" + TypeHierarchy.name)
        Files.createDirectories(serviceDir)
        recordNames.each { Files.createFile(serviceDir.resolve(it)) }
        return root
    }
}
