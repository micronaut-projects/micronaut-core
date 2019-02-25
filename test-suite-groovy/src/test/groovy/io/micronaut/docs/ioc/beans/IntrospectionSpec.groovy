package io.micronaut.docs.ioc.beans

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanProperty
import spock.lang.Specification

class IntrospectionSpec extends Specification {

    void "test retrieve introspection"() {
        given:
        // tag::usage[]
        def introspection = BeanIntrospection.getIntrospection(Person) // <1>
        Person person = introspection.instantiate("John") // <2>
        println("Hello ${person.name}")

        BeanProperty<Person, String> property = introspection.getRequiredProperty("name", String) // <3>
        property.set(person, "Fred") // <4>
        String name = property.get(person) // <5>
        println("Hello ${person.name}")
        // end::usage[]

        expect:
        name == 'Fred'
    }
}
