package io.micronaut.docs.ioc.builders

import io.micronaut.core.beans.BeanIntrospection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IntrospectionBuilderSpec {

    @Test
    fun testIntrospectionBuilder() {
        // tag::builder[]
        val introspection = BeanIntrospection.getIntrospection(Person::class.java)
        val builder: BeanIntrospection.Builder<Person> = introspection.builder()
        val person = builder
            .with("age", 25)
            .with("name", "Fred")
            .build()
        // end::builder[]
        assertEquals(
            Person.builder().name("Fred").age(25).build(),
            person
        )
    }
}
