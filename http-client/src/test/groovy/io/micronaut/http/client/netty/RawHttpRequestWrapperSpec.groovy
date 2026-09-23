package io.micronaut.http.client.netty

import io.micronaut.core.convert.ConversionService
import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.http.HttpRequest
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.AvailableByteArrayBody
import spock.lang.Specification

class RawHttpRequestWrapperSpec extends Specification {

    void "replacing the body releases the raw bytes and sends the new body"() {
        given:
        boolean closed = false
        CloseableByteBody bytes = AvailableByteArrayBody.create(ByteArrayBufferFactory.INSTANCE, "original".bytes)
        CloseableByteBody tracked = [
            close         : { -> closed = true; bytes.close() },
            expectedLength: { -> bytes.expectedLength() },
            move          : { -> bytes.move() },
        ] as CloseableByteBody
        def wrapper = new RawHttpRequestWrapper<Object>(ConversionService.SHARED, HttpRequest.POST("http://localhost/echo", null), tracked)

        expect:
        wrapper.byteBodyDirect().is(tracked)

        when:
        wrapper.body("replacement")

        then:
        closed
        wrapper.byteBodyDirect() == null
        wrapper.body.get() == "replacement"
        wrapper.getBody(String).get() == "replacement"

        when: 'the body is cleared'
        wrapper.body(null)

        then: 'nothing is sent, the raw bytes stay released'
        wrapper.byteBodyDirect() == null
        !wrapper.body.present

        when:
        wrapper.close()

        then:
        noExceptionThrown()
    }
}
