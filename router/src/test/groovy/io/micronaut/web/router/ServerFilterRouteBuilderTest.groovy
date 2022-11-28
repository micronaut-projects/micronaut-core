package io.micronaut.web.router

import spock.lang.Specification

class ServerFilterRouteBuilderTest extends Specification {
    def 'combine ant patterns'(String bean, String method, String combined) {
        expect:
        ServerFilterRouteBuilder.concatAntPatterns(bean, method) == combined

        where:
        bean       | method | combined
        '/foo'     | 'bar'  | '/foo/bar'
        '/foo/**'  | 'bar'  | '/foo/**/bar'
        '/foo/**/' | 'bar'  | '/foo/**/bar'
        '/foo/**/' | '/bar' | '/foo/**/bar'
    }
}
