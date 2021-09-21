package io.micronaut.http

import io.micronaut.http.simple.SimpleHttpResponse
import spock.lang.Specification

class MutableHttpMessageSpec extends Specification {
    void "test does not add duplicate content type header"() {
        given:
        def response = new SimpleHttpResponse()

        when:
        response.contentType("text/html")
        response.contentType("application/json")

        then:
        response.headers.asMap()[HttpHeaders.CONTENT_TYPE] == List.of("application/json")
    }

    void "test does not add duplicate content length header"() {
        given:
        def response = new SimpleHttpResponse()

        when:
        response.contentLength(34)
        response.contentLength(100)

        then:
        response.headers.asMap()[HttpHeaders.CONTENT_LENGTH] == List.of("100")
    }

    void "test does not add duplicate content encoding header"() {
        given:
        def response = new SimpleHttpResponse()

        when:
        response.contentEncoding("en")
        response.contentEncoding("test")

        then:
        response.headers.asMap()[HttpHeaders.CONTENT_ENCODING] == List.of("test")
    }
}
