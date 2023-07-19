package io.micronaut.docs.ioc.builders

import io.micronaut.core.beans.BeanIntrospection
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class IntrospectionBuilderSpec {
    @Test
    fun testIntrospectionBuilder() {
        // tag::builder[]
        val introspection = BeanIntrospection.getIntrospection(
            Person::class.java
        )
        val builder = introspection.builder()
        val person = builder
            .with("age", 25)
            .with("name", "Fred")
            .build()

        // end::builder[]
        Assertions.assertEquals(
            Person("Fred", 25),
            person
        )
    }
}
