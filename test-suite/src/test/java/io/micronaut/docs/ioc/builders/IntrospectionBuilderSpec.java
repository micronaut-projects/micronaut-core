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
            Person.builder().name("Fred").age(25).build(),
            person
        );
    }

    @Test
    void testSuperBuilder() {
        BeanIntrospection<SubBuilder> introspection = BeanIntrospection.getIntrospection(SubBuilder.class);
        BeanIntrospection.Builder<SubBuilder> builder = introspection.builder();
        SubBuilder sub = builder
            .with("foo", "fizz")
            .with("bar", "buzz")
            .build();
        assertEquals(
            new SubBuilder.Builder().bar("buzz").foo("fizz").build(),
            sub
        );
    }
}
