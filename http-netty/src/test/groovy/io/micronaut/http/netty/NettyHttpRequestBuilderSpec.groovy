package io.micronaut.http.netty

import io.micronaut.http.HttpMethod
import io.micronaut.http.simple.SimpleHttpRequest
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

class NettyHttpRequestBuilderSpec extends Specification {

    void "a builder of a non-netty request reflects header changes made after it was created"() {
        given:
        def request = new SimpleHttpRequest(HttpMethod.POST, "/foo?bar=baz", null)
        request.header("X-Before", "a")

        when:
        def builder = NettyHttpRequestBuilder.asBuilder(request)
        request.header("X-After", "b")
        HttpRequest nettyRequest = builder.toHttpRequestWithoutBody()

        then:
        nettyRequest.protocolVersion() == HttpVersion.HTTP_1_1
        nettyRequest.method() == io.netty.handler.codec.http.HttpMethod.POST
        nettyRequest.uri() == "/foo?bar=baz"
        nettyRequest.headers().get("X-Before") == "a"
        nettyRequest.headers().get("X-After") == "b"
    }

    void "toHttpRequestWithoutBody with a request target replaces the uri only on the returned request"() {
        given:
        def request = new SimpleHttpRequest(HttpMethod.GET, "http://example.com/foo?bar=baz", null)
        request.header("X-Custom", "a")
        def builder = NettyHttpRequestBuilder.asBuilder(request)

        when:
        HttpRequest withTarget = builder.toHttpRequestWithoutBody("/foo?bar=baz")

        then:
        withTarget.uri() == "/foo?bar=baz"
        withTarget.method() == io.netty.handler.codec.http.HttpMethod.GET
        withTarget.protocolVersion() == HttpVersion.HTTP_1_1
        withTarget.headers().get("X-Custom") == "a"

        and: "a request built again still has the original uri"
        builder.toHttpRequestWithoutBody().uri() == "http://example.com/foo?bar=baz"
    }
}
