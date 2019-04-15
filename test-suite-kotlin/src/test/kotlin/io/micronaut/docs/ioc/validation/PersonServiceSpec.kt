package io.micronaut.docs.ioc.validation

// tag::imports[]
import io.micronaut.test.annotation.MicronautTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

import javax.inject.Inject
import javax.validation.ConstraintViolationException
// end::imports[]
// tag::test[]
@MicronautTest
class PersonServiceSpec {

    @Inject
    lateinit var personService: PersonService

    @Test
    fun testThatNameIsValidated() {
        val exception = assertThrows(ConstraintViolationException::class.java
        ) { personService.sayHello("") } // <1>

        assertEquals("sayHello.name: must not be blank", exception.message) // <2>
    }
}
// end::test[]
