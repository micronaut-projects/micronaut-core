package io.micronaut.http.simple

import io.micronaut.http.HttpMethod
import spock.lang.Specification

class SimpleHttpRequestMethodNameSpec extends Specification {

    void "a standard method is named after the method"() {
        when:
        def request = new SimpleHttpRequest<>(HttpMethod.POST, '/foo', null)

        then:
        request.method == HttpMethod.POST
        request.methodName == 'POST'
    }

    void "a custom method keeps its name"() {
        when:
        def request = new SimpleHttpRequest<>(HttpMethod.CUSTOM, '/foo', null, 'PROPFIND')

        then:
        request.method == HttpMethod.CUSTOM
        request.methodName == 'PROPFIND'
    }

    void "the factory keeps the name of a custom method"() {
        given:
        def factory = new SimpleHttpRequestFactory()

        expect:
        factory.create(HttpMethod.CUSTOM, '/foo', 'PROPFIND').methodName == 'PROPFIND'
        factory.create(HttpMethod.GET, '/foo').methodName == 'GET'
    }
}
