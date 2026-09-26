package io.micronaut.http.netty

import io.micronaut.core.convert.ConversionService
import io.micronaut.core.type.Argument
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

class NettyMutableHttpResponseSpec extends Specification {

    void "default headers are multi-valued and case-insensitive"() {
        given:
        def response = new NettyMutableHttpResponse<>(ConversionService.SHARED)

        when:
        response.header("X-Multi", "a")
        response.header("x-multi", "b")
        response.getHeaders().add("X-MULTI", "c")
        for (int i = 0; i < 40; i++) {
            response.header("X-Header-" + i, "v" + i)
        }

        then:
        response.getHeaders().getAll("x-Multi") == ["a", "b", "c"]
        response.getHeaders().get("X-MULTI") == "a"
        response.getNettyHeaders().size() == 43
        (0..<40).every { response.getHeaders().get("x-header-" + it) == "v" + it }

        when:
        response.getHeaders().remove("X-multi")

        then:
        !response.getHeaders().contains("X-Multi")
        response.getNettyHeaders().size() == 40
    }

    void "default netty headers do not validate, like DefaultHttpHeaders(false)"() {
        given:
        def response = new NettyMutableHttpResponse<>(ConversionService.SHARED)
        def reference = new DefaultHttpHeaders(false)

        when:
        response.getNettyHeaders().add("Bad Name", "bad\r\nvalue")
        reference.add("Bad Name", "bad\r\nvalue")

        then:
        response.getHeaders().get("Bad Name") == "bad\r\nvalue"
        response.getNettyHeaders() == reference

        when:
        response.getNettyHeaders().add((CharSequence) null, "x")

        then:
        thrown(NullPointerException)
    }

    void "micronaut headers still validate names"() {
        given:
        def response = new NettyMutableHttpResponse<>(ConversionService.SHARED)

        when:
        response.header("Bad Name", "value")

        then:
        thrown(IllegalArgumentException)
    }

    void "default headers copy into full responses"() {
        given:
        def response = new NettyMutableHttpResponse<>(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, ConversionService.SHARED)
        response.header("X-A", "1")
        response.header("X-A", "2")

        expect:
        response.toFullHttpResponse().headers().getAll("x-a") == ["1", "2"]
        response.getNettyHeaders().copy().getAll("x-a") == ["1", "2"]
    }

    void "body conversion works and follows body changes"() {
        given:
        def response = new NettyMutableHttpResponse<>(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, "10", ConversionService.SHARED)

        expect:
        response.getBody(Integer).get() == 10
        response.getBody(Integer).get() == 10
        response.getBody(String).get() == "10"
        response.getBody(Argument.OBJECT_ARGUMENT).get() == "10"

        when:
        response.body("20")

        then:
        response.getBody(Integer).get() == 20
        response.getBody(Long).get() == 20L

        when:
        response.body(null)

        then:
        !response.getBody(Integer).isPresent()
    }

    void "body change without prior conversion"() {
        given:
        def response = new NettyMutableHttpResponse<>(ConversionService.SHARED)

        when:
        response.body("5")

        then:
        response.getBody(Integer).get() == 5
    }
}
