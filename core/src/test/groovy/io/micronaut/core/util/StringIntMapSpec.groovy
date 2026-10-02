package io.micronaut.core.util

import spock.lang.Specification

class StringIntMapSpec extends Specification {
    def simple() {
        given:
        def map = new StringIntMap(4)

        when:
        map.put("foo", 1)
        then:
        map.get("foo", -1) == 1
        map.get("bar", -1) == -1

        when:
        map.put("bar", 2)
        then:
        map.get("foo", -1) == 1
        map.get("bar", -1) == 2
        map.get("fizz", -1) == -1

        when:
        map.put("fizz", 3)
        then:
        map.get("foo", -1) == 1
        map.get("bar", -1) == 2
        map.get("fizz", -1) == 3
        map.get("buzz", -1) == -1

        when:
        map.put("buzz", 4)
        then:
        map.get("foo", -1) == 1
        map.get("bar", -1) == 2
        map.get("fizz", -1) == 3
        map.get("buzz", -1) == 4
    }

    def "empty map returns the supplied default"() {
        expect:
        new StringIntMap(0).get("missing", Integer.MIN_VALUE) == Integer.MIN_VALUE
    }

    def "lookup handles collisions and wraparound"() {
        given:
        def map = new StringIntMap(3)
        def values = [Integer.MIN_VALUE, -1, Integer.MAX_VALUE]
        keys.eachWithIndex { key, i -> map.put(key, values[i]) }

        expect:
        keys.withIndex().every { key, i ->
            map.get(new String(key.toCharArray()), 42) == values[i]
        }
        map.get(missing, 42) == 42

        when:
        map.put(new String(keys[1].toCharArray()), 42)

        then:
        thrown(IllegalArgumentException)
        map.get(keys[1], 42) == -1

        where:
        keys                       | missing
        ["AaAa", "BBBB", "AaBB"]   | "BBAa"
        ["g", "o", "w"]            | "?"
    }
}
