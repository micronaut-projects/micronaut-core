package io.micronaut.docs.ioc.validation.custom

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import jakarta.inject.Inject
import javax.validation.ConstraintViolationException

@MicronautTest
class DurationPatternValidatorSpec extends Specification {

    @Inject HolidayService holidayService

    // tag::test[]
    void "test test custom validator"() {
        when:"A custom validator is used"
        holidayService.startHoliday("Fred", "junk") // <1>

        then:"A validation error occurs"
        def e = thrown(ConstraintViolationException)
        e.message == "startHoliday.duration: invalid duration (junk), additional custom message" //  <2>
    }
    // end::test[]
}
