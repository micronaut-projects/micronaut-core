package io.micronaut.docs.ioc.introspection

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@MicronautTest
class PersonSpec {

    @Test
    fun testPersonIntrospection() {
        // tag::usage[]
        val introspection = BeanIntrospection.getIntrospection(Person::class.java)
        val person = introspection.instantiate("John", 42)

        Assertions.assertEquals("John", person.name())
        Assertions.assertEquals(42, person.age())
        // end::usage[]
    }
}
