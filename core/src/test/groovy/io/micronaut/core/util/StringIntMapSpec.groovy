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
}
