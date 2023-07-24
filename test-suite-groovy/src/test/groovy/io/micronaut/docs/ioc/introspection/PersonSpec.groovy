package io.micronaut.docs.ioc.introspection;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.test.extensions.spock.annotation.MicronautTest;
import org.junit.jupiter.api.Test
import spock.lang.Specification;

@MicronautTest
class PersonSpec extends Specification {

    @Test
    void "test person introspection"() {
        // tag::usage[]
        BeanIntrospection<Person> introspection = BeanIntrospection.getIntrospection(Person.class);
        Person person = introspection.instantiate('John', 42)

        person.name() == 'John'
        person.age() == 42
        // end::usage[]
    }
}
