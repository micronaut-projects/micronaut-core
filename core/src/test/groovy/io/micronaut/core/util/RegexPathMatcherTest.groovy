package io.micronaut.core.util

import spock.lang.Specification

class RegexPathMatcherTest extends Specification {

    void 'test matches()'() {
        given:
        def matcher = new RegexPathMatcher()
        expect:
        matcher.matches('^.*$', "/api/v2/endpoint")
        !matcher.matches('^/api/v1/.*', "/api/v2/endpoint")
    }
}
