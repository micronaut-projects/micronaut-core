package io.micronaut.docs.ioc.introspection

import io.micronaut.core.beans.BeanIntrospection
import spock.lang.Specification

class PersonSpec extends Specification {

    void "test person introspection"() {
        expect:
        // tag::usage[]
        BeanIntrospection<Person> introspection = BeanIntrospection.getIntrospection(Person)
        Person person = introspection.instantiate('John', 42)

        assert person.name() == 'John'
        assert person.age() == 42
        // end::usage[]
    }
}
