package io.micronaut.http.context

import spock.lang.Specification

class ContextPathQuerySpec extends Specification {

    void "a query-only request URI keeps the trailing slash of the context path: #contextPath"(String contextPath, String expected) {
        expect:
        ContextPathUtils.prepend(URI.create('?a=b'), contextPath).toString() == expected

        where:
        contextPath | expected
        '/bar/'     | '/bar/?a=b'
        '/bar//'    | '/bar/?a=b'
        'bar/'      | 'bar/?a=b'
    }
}
