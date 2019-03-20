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
        reg.find(NotBlank, String)
        reg.find(NotBlank, String).is(reg.find(NotBlank, StringBuffer))
        reg.find(NotBlank, String).is(reg.find(NotBlank, CharSequence))
    }
}
