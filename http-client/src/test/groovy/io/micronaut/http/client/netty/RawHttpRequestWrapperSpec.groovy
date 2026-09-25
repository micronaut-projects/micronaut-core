package io.micronaut.http.client.netty

import io.micronaut.core.convert.ConversionService
import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.cookie.Cookie
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

    void "a cookie is added to the Cookie header of the wrapped request, replacing one of the same name"() {
        given:
        CloseableByteBody bytes = AvailableByteArrayBody.create(ByteArrayBufferFactory.INSTANCE, new byte[0])
        def request = HttpRequest.GET("http://localhost/echo").header(HttpHeaders.COOKIE, "kept=1; replaced=old")
        def wrapper = new RawHttpRequestWrapper<Object>(ConversionService.SHARED, request, bytes)

        when:
        wrapper.cookie(Cookie.of("replaced", "new")).cookie(Cookie.of("added", "2"))

        then:
        request.headers.get(HttpHeaders.COOKIE) == "kept=1; replaced=new; added=2"

        cleanup:
        wrapper.close()
    }
}
