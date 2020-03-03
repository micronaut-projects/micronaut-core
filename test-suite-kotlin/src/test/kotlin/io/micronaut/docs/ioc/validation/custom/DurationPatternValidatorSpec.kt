package io.micronaut.docs.ioc.validation.custom

import io.micronaut.test.annotation.MicronautTest
import org.junit.jupiter.api.Test

import javax.inject.Inject
import javax.validation.ConstraintViolationException

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows

@MicronautTest
internal class DurationPatternValidatorSpec {

    // tag::test[]
    @Inject
    lateinit var holidayService: HolidayService

    @Test
    fun testCustomValidator() {
        val exception = assertThrows(ConstraintViolationException::class.java
        ) { holidayService.startHoliday("Fred", "junk") }   // <1>

        assertEquals("startHoliday.duration: invalid duration (junk)", exception.message) // <2>
    }
    // end::test[]
}
