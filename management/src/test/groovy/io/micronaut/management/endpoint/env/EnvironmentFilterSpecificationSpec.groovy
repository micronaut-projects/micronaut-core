package io.micronaut.management.endpoint.env

import spock.lang.Specification

import java.util.regex.Pattern

class EnvironmentFilterSpecificationSpec extends Specification {

    private Pattern PATTERN_MASK = ~"(?i)test.*"
    private String LITERAL_MASK = 'test'

    def "masks all by default"() {
        when:
        def spec = new EnvironmentFilterSpecification()

        then:
        spec.test("test") == EnvironmentFilterSpecification.FilterResult.MASK
    }

    def "can unmask given patterns"() {
        when:
        def spec = new EnvironmentFilterSpecification() // defaults to maskAll()
                .exclude(PATTERN_MASK)

        then:
        spec.test("test") == EnvironmentFilterSpecification.FilterResult.PLAIN
        spec.test("test2") == EnvironmentFilterSpecification.FilterResult.PLAIN
        spec.test("another") == EnvironmentFilterSpecification.FilterResult.MASK
    }

    def "can unmask static names"() {
        when:
        def spec = new EnvironmentFilterSpecification() // defaults to maskAll()
                .exclude(LITERAL_MASK)

        then:
        spec.test("test") == EnvironmentFilterSpecification.FilterResult.PLAIN
        spec.test("test2") == EnvironmentFilterSpecification.FilterResult.MASK
    }

    def "can be set to unmask all"() {
        when:
        def spec = new EnvironmentFilterSpecification().maskNone()

        then:
        spec.test("test") == EnvironmentFilterSpecification.FilterResult.PLAIN
    }

    def "can mask given patterns"() {
        when:
        def spec = new EnvironmentFilterSpecification().maskNone()
                .exclude(PATTERN_MASK)

        then:
        spec.test("test") == EnvironmentFilterSpecification.FilterResult.MASK
        spec.test("TEST_2") == EnvironmentFilterSpecification.FilterResult.MASK
        spec.test("another") == EnvironmentFilterSpecification.FilterResult.PLAIN
    }

    def "can set legacy mode"() {
        when:
        def spec = new EnvironmentFilterSpecification().legacyMasking()

        then:
        spec.test("test") == EnvironmentFilterSpecification.FilterResult.PLAIN
        spec.test("test2") == EnvironmentFilterSpecification.FilterResult.PLAIN
        spec.test("secret.key") == EnvironmentFilterSpecification.FilterResult.MASK
        spec.test("my.certificate") == EnvironmentFilterSpecification.FilterResult.MASK
        spec.test("SOME_PASSWORD") == EnvironmentFilterSpecification.FilterResult.MASK

        when:
        spec = spec.exclude(PATTERN_MASK)

        then:
        spec.test("test") == EnvironmentFilterSpecification.FilterResult.MASK
        spec.test("test2") == EnvironmentFilterSpecification.FilterResult.MASK
        spec.test("another") == EnvironmentFilterSpecification.FilterResult.PLAIN
    }
}
