package io.micronaut.core.util

import spock.lang.Specification

class ArrayUtilsSpec extends Specification {

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
