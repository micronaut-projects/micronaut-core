package io.micronaut.core.util

import spock.lang.Specification

class ArrayUtilsSpec extends Specification {

    void 'test toWrapperArray()'() {
        expect:
        ArrayUtils.toWrapperArray([1, 2] as int[]) == [1, 2] as Integer[]
        ArrayUtils.toWrapperArray([1, 2] as double[]) == [1, 2] as Double[]
        ArrayUtils.toWrapperArray([1, 2] as int[]).class == Integer[].class
    }

    void 'test toPrimitiveArray()'() {
        expect:
        ArrayUtils.toPrimitiveArray([1, 2] as Integer[]) == [1, 2] as int[]
        ArrayUtils.toPrimitiveArray([1, 2] as Integer[]).class == int[].class
    }

    void "test reverse iterator works"() {
        given:
        Iterator<String> reversedIterator = ArrayUtils.reverseIterator("a", "b", "c")

        when:
        def val
        while (reversedIterator.hasNext()) {
            val = reversedIterator.next()
        }

        then:
        noExceptionThrown()
        val == "a"
    }

}
