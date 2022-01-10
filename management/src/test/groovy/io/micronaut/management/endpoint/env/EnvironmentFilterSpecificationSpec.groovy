package io.micronaut.management.endpoint.env

import spock.lang.Specification

class EnvironmentFilterSpecificationSpec extends Specification {

    private EnvironmentFilterSpecification.EnvironmentFilterNamePredicate TEST_REGEX_PREDICATE = EnvironmentFilterSpecification.regularExpressionPredicate("test")
    private EnvironmentFilterSpecification.EnvironmentFilterNamePredicate TEST_STATIC_PREDICATE = { it -> it == 'test' }

    def "masks all by default"() {
        when:
        def spec = new EnvironmentFilterSpecification(null)

        then:
        spec.test("test") == EnvironmentFilterSpecification.FilterResult.MASK
    }

    def "can unmask given patterns"() {
        when:
        def spec = new EnvironmentFilterSpecification(null) // defaults to maskAll()
                .exclude(TEST_REGEX_PREDICATE)

        then:
        spec.test("test") == EnvironmentFilterSpecification.FilterResult.PLAIN
        spec.test("test2") == EnvironmentFilterSpecification.FilterResult.MASK
    }

    def "can unmask static names"() {
        when:
        def spec = new EnvironmentFilterSpecification(null) // defaults to maskAll()
                .exclude(TEST_STATIC_PREDICATE)

        then:
        spec.test("test") == EnvironmentFilterSpecification.FilterResult.PLAIN
        spec.test("test2") == EnvironmentFilterSpecification.FilterResult.MASK
    }

    def "can be set to unmask all"() {
        when:
        def spec = new EnvironmentFilterSpecification(null).maskNone()

        then:
        spec.test("test") == EnvironmentFilterSpecification.FilterResult.PLAIN
    }

    def "can mask given patterns"() {
        when:
        def spec = new EnvironmentFilterSpecification(null).maskNone()
                .exclude(TEST_REGEX_PREDICATE)

        then:
        spec.test("test") == EnvironmentFilterSpecification.FilterResult.MASK
        spec.test("test2") == EnvironmentFilterSpecification.FilterResult.PLAIN
    }

    def "can set legacy mode"() {
        when:
        def spec = new EnvironmentFilterSpecification(null).legacyMasking()

        then:
        spec.test("test") == EnvironmentFilterSpecification.FilterResult.PLAIN
        spec.test("test2") == EnvironmentFilterSpecification.FilterResult.PLAIN
        spec.test("secret.key") == EnvironmentFilterSpecification.FilterResult.MASK
        spec.test("my.certificate") == EnvironmentFilterSpecification.FilterResult.MASK
        spec.test("SOME_PASSWORD") == EnvironmentFilterSpecification.FilterResult.MASK

        when:
        spec = spec.exclude(TEST_REGEX_PREDICATE)

        then:
        spec.test("test") == EnvironmentFilterSpecification.FilterResult.MASK
        spec.test("test2") == EnvironmentFilterSpecification.FilterResult.PLAIN
    }
}
