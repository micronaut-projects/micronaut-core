package io.micronaut.docs.ioc.validation.custom

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import jakarta.inject.Inject
import javax.validation.ConstraintViolationException

@MicronautTest
internal class DurationPatternValidatorSpec {

    // tag::test[]
    @Inject
    lateinit var holidayService: HolidayService

    @Test
    fun testCustomValidator() {
        val exception = assertThrows(ConstraintViolationException::class.java) {
            holidayService.startHoliday("Fred", "junk") // <1>
        }

        assertEquals("startHoliday.duration: invalid duration (junk), additional custom message", exception.message) // <2>
    }
    // end::test[]
}
