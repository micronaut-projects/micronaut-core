package io.micronaut.docs.ioc.introspection;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.test.extensions.spock.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest
class PersonTest {

    @Test
    void testPersonIntrospection() {
        // tag::usage[]
        BeanIntrospection<Person> introspection = BeanIntrospection.getIntrospection(Person.class);
        Person person = introspection.instantiate("John", 42);

        Assertions.assertEquals("John", person.name());
        Assertions.assertEquals(42, person.age());
        // end::usage[]
    }
}
