package io.micronaut.validation.validator

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import javax.validation.ConstraintViolationException

class ValidatorSpec {

    @Test
    fun testValidateInstance() {
        val context = ApplicationContext.run()
        val validator = context.getBean(Validator::class.java)

        val person = Person("", 10)
        val violations = validator.validate(person)
//      TODO: currently fails because bean introspection API does not handle data classes
//        assertEquals(2, violations.size)
        context.close()
    }

    @Test
    fun testValidateNew() {
        val context = ApplicationContext.run()
        val validator = context.getBean(Validator::class.java).forExecutables()

        try {
            val person = validator.createValid(Person::class.java, "", 10)
            fail<Any>("should have failed with validation errors")
        } catch (e: ConstraintViolationException) {
            assertEquals(2, e.constraintViolations.size)
        }
        context.close()
    }
}