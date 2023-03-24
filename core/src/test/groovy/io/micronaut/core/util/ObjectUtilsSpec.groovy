package io.micronaut.core.util

import spock.lang.Specification
import spock.lang.Unroll

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

    @Unroll("ObjectUtils.coerceToBoolean with argument #obj returns #expected")
    def "ObjectUtils::coerceToBoolean"(boolean expected, Object obj) {
        expect:
        expected == ObjectUtils.coerceToBoolean(obj)
        where:
        expected | obj
        false    | null
        false    | Boolean.FALSE
        true     | Boolean.TRUE
        true     | "string"
        false    | ""
        false    | 0L
        false    | new BigDecimal("0.0")
        false    | 0
        false    | 0.0f
        true     | 1L
        true     | new BigDecimal("0.1")
        true     | 1
        true    | -1
        true     | 0.1f
        false    | Collections.emptyList()
        true     | Collections.singletonList("1")
        false    | Collections.emptyMap()
        true     | Collections.singletonMap("foo", "bar")
        false    | new String[] {}
        true     | new String[] {"foo"}
        false    | Optional.empty()
        true     | Optional.of("foo")
    }

}
