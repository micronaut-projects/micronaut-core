package io.micronaut.core.convert.value

import spock.lang.Specification

class ConvertibleValuesSpec extends Specification {

    void "test convertible values as Map"() {
        given:
        def vals = ConvertibleValues.of(
                'foo':'bar',
                'num':1,
                'baz': ['one': 1, 'two': 2]
        )

        when:
        def map = vals.asMap(String, String)

        then:
        map.foo == 'bar'
        map.num == '1'

        when:
        def props = vals.asProperties()

        then:
        props.size() == 2
        props.foo == 'bar'
        props.num == '1'
    }
}
