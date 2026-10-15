package io.micronaut.core.beans

import io.micronaut.core.type.Argument
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class AbstractBeanMethodArgumentIndexSpec extends Specification {

    static class TestMethod extends AbstractBeanMethod<Object, Object> {
        TestMethod(Argument<?>... arguments) {
            // the introspection is only stored, never used by argumentIndexOf
            super(null, Argument.OBJECT_ARGUMENT, "test", null, arguments)
        }

        @Override
        protected Object invokeInternal(Object instance, Object... arguments) { null }
    }

    private static Argument<?>[] args(int count) {
        (0..<count).collect { Argument.of(String, "arg$it".toString()) } as Argument<?>[]
    }

    def "argumentIndexOf finds all #count arguments"() {
        given:
        def method = new TestMethod(args(count))

        expect:
        (0..<count).every { method.argumentIndexOf("arg$it".toString()) == it }
        method.argumentIndexOf("missing") == -1
        method.argumentIndexOf("missing") == -1  // cached path

        where:
        // ImmutableStringIntMap.LINEAR_SCAN_THRESHOLD is 4 but package-private, so 4 and 5 are
        // hard-coded here to cover both sides of the scan/hash-table layout boundary.
        count << [0, 1, 3, 4, 5, 8, 9, 20]
    }

    def "argumentIndexOf rejects null"() {
        when:
        new TestMethod(args(2)).argumentIndexOf(null)

        then:
        thrown(NullPointerException)
    }

    def "argumentIndexOf returns the first of #count arguments sharing a name"() {
        given: 'a Kotlin suspend method parameter can share its name with the synthetic continuation'
        Argument<?>[] arguments = (args(count - 1).toList() + [Argument.of(String, "arg0")]) as Argument<?>[]
        def method = new TestMethod(arguments)

        expect:
        method.argumentIndexOf("arg0") == 0
        method.getArgument("arg0").get().is(arguments[0])
        (1..<count - 1).every { method.argumentIndexOf("arg$it".toString()) == it }

        where:
        count << [2, 4, 5, 9]
    }

    def "concurrent first calls all see correct indexes"() {
        given:
        def pool = Executors.newFixedThreadPool(8)

        when:
        boolean allCorrect = (1..200).every {
            def method = new TestMethod(args(12))
            def start = new CountDownLatch(1)
            def futures = (1..8).collect {
                pool.submit({
                    start.await()
                    (0..<12).every { i -> method.argumentIndexOf("arg$i".toString()) == i }
                } as Callable<Boolean>)
            }
            start.countDown()
            futures.every { it.get() }
        }

        then:
        allCorrect

        cleanup:
        pool.shutdownNow()
    }
}
