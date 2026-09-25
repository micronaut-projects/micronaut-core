package io.micronaut.http.netty

import io.micronaut.core.convert.ConversionService
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.micronaut.http.netty.stream.StreamedHttpResponse
import spock.lang.Specification

class NettyHttpResponseBuilderSpec extends Specification {

    void "toStreamResponse keeps headers and protocol version of a full response"() {
        given:
        def response = new NettyMutableHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.CREATED, Unpooled.copiedBuffer("hello".bytes), ConversionService.SHARED)
        response.header("X-Custom", "foo")

        when:
        StreamedHttpResponse streamed = NettyHttpResponseBuilder.toStreamResponse(response)

        then:
        streamed.status() == HttpResponseStatus.CREATED
        streamed.protocolVersion() == HttpVersion.HTTP_1_0
        streamed.headers().get("X-Custom") == "foo"
    }

    void "toStreamHttpResponse keeps protocol version"() {
        given:
        def response = new NettyMutableHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER, ConversionService.SHARED)
        response.header("X-Custom", "foo")

        when:
        StreamedHttpResponse streamed = response.toStreamHttpResponse()

        then:
        streamed.protocolVersion() == HttpVersion.HTTP_1_0
        streamed.headers().get("X-Custom") == "foo"
    }
}
