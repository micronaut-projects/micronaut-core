package io.micronaut.validation.validator

import io.micronaut.context.ApplicationContext
import io.micronaut.validation.validator.constraints.ConstraintValidatorRegistry
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.validation.constraints.NotBlank

class ConstraintValidatorRegistrySpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()
    @Shared ConstraintValidatorRegistry reg = context.getBean(ConstraintValidatorRegistry)

    void "test find constraint validators"() {
        expect:
        reg.getConstraintValidator(NotBlank, String)
        reg.getConstraintValidator(NotBlank, String).is(reg.getConstraintValidator(NotBlank, StringBuffer))
        reg.getConstraintValidator(NotBlank, String).is(reg.getConstraintValidator(NotBlank, CharSequence))
    }
}
