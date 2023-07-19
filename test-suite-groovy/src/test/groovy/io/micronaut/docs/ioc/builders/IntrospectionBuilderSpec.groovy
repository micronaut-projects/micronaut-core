package io.micronaut.docs.ioc.builders

import io.micronaut.core.beans.BeanIntrospection
import spock.lang.Specification


class IntrospectionBuilderSpec extends Specification {
    void "test introspection builder"() {
        // tag::builder[]
        BeanIntrospection<Person> introspection = BeanIntrospection.getIntrospection(Person.class);
        BeanIntrospection.Builder<Person> builder = introspection.builder();
        Person person = builder
                .with("age", 25)
                .with("name", "Fred")
                .build();

        // end::builder[]

        expect:
        new Person("Fred", 25) == person
    }
}
