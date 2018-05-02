package io.micronaut.http.client

import io.micronaut.http.HttpRequest
import spock.lang.Specification

class BasicAuthSpec extends Specification {

    def "basicAuth() sets Authorization Header with Basic base64(username:password)"() {
        when:
        HttpRequest request = HttpRequest.GET("/home").basicAuth('sherlock', 'password')

        then:
        request.headers.get('Authorization')
        request.headers.get('Authorization') == "Basic ${'sherlock:password'.bytes.encodeBase64().toString()}"
    }
}
