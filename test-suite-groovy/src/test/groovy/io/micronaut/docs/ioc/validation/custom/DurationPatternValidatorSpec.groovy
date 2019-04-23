package io.micronaut.docs.ioc.validation.custom

import io.micronaut.test.annotation.MicronautTest
import spock.lang.PendingFeature
import spock.lang.Specification

import javax.inject.Inject
import javax.validation.ConstraintViolationException


@MicronautTest
class DurationPatternValidatorSpec extends Specification {

    @Inject HolidayService holidayService

    @PendingFeature(reason = "default message support not yet implemented")
    // tag::test[]
    void "test test custom validator"() {
        when:"A custom validator is used"
        holidayService.startHoliday("Fred", "junk") // <1>

        then:"A validation error occurs"
        def e = thrown(ConstraintViolationException)
        e.message == "startHoliday.duration: invalid duration (junk)" //  <2>
    }
    // end::test[]
}
