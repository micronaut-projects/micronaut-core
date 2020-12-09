package io.micronaut.validation.validator.constraints.custom

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.validation.validator.Validator
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.validation.Valid

class CustomConstraintsSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run()
    @Shared
    Validator validator = applicationContext.getBean(Validator)

    void "test validation where pojo with inner custom constraint fails"() {
        given:
        TestInvalid testInvalid = new TestInvalid(invalidInner: new TestInvalid.InvalidInner())

        when:
        def violations = validator.validate(testInvalid)

        then:
        violations.size() == 1
        violations[0].message == "invalid"
    }

    void "test validation where pojo with outer custom constraint fails"() {
        given:
        TestInvalid testInvalid = new TestInvalid(invalidOuter: new InvalidOuter())

        when:
        def violations = validator.validate(testInvalid)

        then:
        violations.size() == 1
        violations[0].message == "invalid"
    }

    void "test validation where pojo with inner and outer custom constraint both fail"() {
        given:
        TestInvalid testInvalid = new TestInvalid(
                invalidInner: new TestInvalid.InvalidInner(),
                invalidOuter: new InvalidOuter())

        when:
        def violations = validator.validate(testInvalid)

        then:
        violations.size() == 2
        violations[0].message == "invalid"
        violations[1].message == "invalid"
    }

    void "test validation where inner custom constraint fails"() {
        given:
        TestInvalid.InvalidInner invalidInner = new TestInvalid.InvalidInner()

        when:
        def violations = validator.validate(invalidInner)

        then:
        violations.size() == 1
        violations[0].message == "invalid"
    }

    void "test validation where outer custom constraint fails"() {
        given:
        InvalidOuter invalidOuter = new InvalidOuter()

        when:
        def violations = validator.validate(invalidOuter)

        then:
        violations.size() == 1
        violations[0].message == "invalid"
    }
}

@Introspected
class TestInvalid {
    @Valid
    InvalidInner invalidInner

    @Valid
    InvalidOuter invalidOuter

    @Introspected
    @AlwaysInvalidConstraint
    static class InvalidInner {}
}

@Introspected
@AlwaysInvalidConstraint
class InvalidOuter {}