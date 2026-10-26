package io.micronaut.http.client.jdk

import io.micronaut.core.convert.ConversionService
import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.AvailableByteArrayBody
import io.micronaut.http.cookie.Cookie
import spock.lang.Specification

class RawHttpRequestWrapperSpec extends Specification {

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
