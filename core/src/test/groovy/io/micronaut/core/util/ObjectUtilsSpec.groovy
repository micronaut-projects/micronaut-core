package io.micronaut.core.util

import spock.lang.Specification

class ObjectUtilsSpec extends Specification {

    def "validate hash2 function"() {
        expect:
            ObjectUtils.hash(o1, o2) == Objects.hash([o1, o2] as Object[])
        where:
            o1 << ["abc", null, "xyz", null]
            o2 << [null, null, "abc", "foo"]
    }

    def "validate hash4 function"() {
        expect:
            ObjectUtils.hash(o1, o2, o3) == Objects.hash([o1, o2, o3] as Object[])
        where:
            o1 << ["abc", null, "xyz", null]
            o2 << [null, null, "abc", "foo"]
            o3 << ["abc", null, "xyz", null]
    }

}
