package io.micronaut.docs.ioc.validation.pojo

// tag::imports[]
import io.micronaut.docs.ioc.validation.Person
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.validation.validator.Validator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

import javax.inject.Inject
import javax.validation.ConstraintViolationException

// end::imports[]
// tag::test[]
@MicronautTest
class PersonServiceSpec {

    // tag::validator[]
    @Inject
    lateinit var validator: Validator

    @Test
    fun testThatPersonIsValidWithValidator() {
        val person = Person("", 10)
        val constraintViolations = validator.validate(person)  // <1>

        assertEquals(2, constraintViolations.size) // <2>
    }
    // end::validator[]

    // tag::validate-service[]
    @Inject
    lateinit var personService: PersonService

    @Test
    fun testThatPersonIsValid() {
        val person = Person("", 10)
        val exception = assertThrows(ConstraintViolationException::class.java  // <1>
        ) { personService.sayHello(person) }

        assertEquals(2, exception.constraintViolations.size) // <2>
    }
    // end::validate-service[]
}
// end::test[]
