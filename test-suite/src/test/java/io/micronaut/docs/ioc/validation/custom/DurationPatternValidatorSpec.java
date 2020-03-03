package io.micronaut.docs.ioc.validation.custom;

import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.validation.ConstraintViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class DurationPatternValidatorSpec {

    // tag::test[]
    @Inject HolidayService holidayService;

    @Test
    void testCustomValidator() {
        final ConstraintViolationException exception =
                assertThrows(ConstraintViolationException.class, () ->
                        holidayService.startHoliday("Fred", "junk")   // <1>
                );

        assertEquals("startHoliday.duration: invalid duration (junk)", exception.getMessage()); // <2>
    }
    // end::test[]
}
