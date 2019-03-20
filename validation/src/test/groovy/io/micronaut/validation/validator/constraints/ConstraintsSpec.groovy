package io.micronaut.validation.validator.constraints

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty

class ConstraintsSpec extends Specification {
    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()
    @Shared
    ConstraintValidatorRegistry reg = context.getBean(ConstraintValidatorRegistry)

    @Unroll
    void "test #constraint constraint for value [#value]"() {
        expect:
        reg.find(constraint, value?.getClass() ?: Object)
                .isValid(value, null) == isValid

        where:
        constraint | value          | isValid
        NotBlank   | ""             | false
        NotBlank   | null           | false
        NotBlank   | "  "           | false
        NotBlank   | "foo"          | true
        NotEmpty   | ""             | false
        NotEmpty   | []             | false
        NotEmpty   | [] as String[] | false
        NotEmpty   | [] as int[]    | false
        NotEmpty   | [1]            | true
    }
}
