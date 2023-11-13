package io.micronaut.docs.ioc.introspection

import io.micronaut.core.beans.BeanIntrospection
import org.junit.jupiter.api.Test
import spock.lang.Specification

class PersonSpec extends Specification {

    @Test
    void "test person introspection"() {
        // tag::usage[]
        BeanIntrospection<Person> introspection = BeanIntrospection.getIntrospection(Person)
        Person person = introspection.instantiate('John', 42)

        person.name() == 'John'
        person.age() == 42
        // end::usage[]
    }
}
