package io.micronaut.core.beans

import spock.lang.Specification

class BeanIntrospectionPropertyIndexSpec extends Specification {

    def "default propertyIndexOf rejects a null name"() {
        when:
        new BeanIntrospectionConstructorArgumentIndexSpec.ArgsOnlyIntrospection().propertyIndexOf(null)

        then:
        thrown(NullPointerException)
    }

    def "default property lookups by name still answer empty for a null name"() {
        given:
        def introspection = new BeanIntrospectionConstructorArgumentIndexSpec.ArgsOnlyIntrospection()

        expect:
        !introspection.getProperty((String) null).isPresent()
        !introspection.getReadProperty((String) null).isPresent()
        !introspection.getWriteProperty((String) null).isPresent()
    }
}
