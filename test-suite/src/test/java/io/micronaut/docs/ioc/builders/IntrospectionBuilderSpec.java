package io.micronaut.docs.ioc.builders;

import io.micronaut.core.beans.BeanIntrospection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntrospectionBuilderSpec {

    @Test
    void testIntrospectionBuilder() {
        // tag::builder[]
        BeanIntrospection<Person> introspection = BeanIntrospection.getIntrospection(Person.class);
        BeanIntrospection.Builder<Person> builder = introspection.builder();
        Person person = builder
            .with("age", 25)
            .with("name", "Fred")
            .build();

        // end::builder[]

        assertEquals(
            new Person("Fred", 25),
            person
        );
    }
}
