package io.micronaut.http.filter

import spock.lang.Specification

class BaseFilterProcessorSpec extends Specification {
    def 'combine ant patterns'(String bean, String method, String combined) {
        expect:
        BaseFilterProcessor.concatAntPatterns(bean, method) == combined

        where:
        bean       | method | combined
        '/foo'     | 'bar'  | '/foo/bar'
        '/foo/**'  | 'bar'  | '/foo/**/bar'
        '/foo/**/' | 'bar'  | '/foo/**/bar'
        '/foo/**/' | '/bar' | '/foo/**/bar'
    }
}
