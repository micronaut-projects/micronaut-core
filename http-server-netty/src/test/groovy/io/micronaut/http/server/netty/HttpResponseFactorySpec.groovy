package io.micronaut.http.server.netty

import io.micronaut.http.HttpResponseFactory
import io.micronaut.http.HttpStatus
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

@MicronautTest
class HttpResponseFactorySpec extends Specification {

    void "custom reason phrase set in MutableHttpResponse is accessible"() {
        given:
        def httpStatus = HttpStatus.INTERNAL_SERVER_ERROR
        def customReason = 'The application failed'


        when:
        def response = HttpResponseFactory.INSTANCE.status(httpStatus, customReason)

        then:
        response.status() == httpStatus
        response.reason() == customReason
    }
}
