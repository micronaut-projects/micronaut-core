package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class KeepAliveSpec extends Specification {
    // this spec confirms behavior of the netty HttpServerKeepAliveHandler

    def 'expect close'(FullHttpRequest request, FullHttpResponse expectedResponse) {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'KeepAliveSpec'])
        def server = ((NettyHttpServer) ctx.getBean(EmbeddedServer)).buildEmbeddedChannel(false)
        def client = new EmbeddedChannel(new HttpClientCodec(), new HttpObjectAggregator(1024))
        EmbeddedTestUtil.connect(server, client)

        when:
        client.writeOutbound(request)
        EmbeddedTestUtil.advance(server, client)
        then:
        FullHttpResponse response = client.readInbound()
        response.headers().remove(HttpHeaderNames.DATE)
        // HttpResponse.equals doesn't work because the types of response are different
        response.protocolVersion() == expectedResponse.protocolVersion()
        response.status() == expectedResponse.status()
        response.headers() == expectedResponse.headers()
        response.content() == expectedResponse.content()
        !server.isOpen()

        cleanup:
        response.release()
        server.finishAndReleaseAll()
        client.finishAndReleaseAll()
        ctx.close()

        where:
        request                                                                                   | expectedResponse
        fullRequest(HttpVersion.HTTP_1_0, HttpMethod.GET, "/keep-alive")                          | fullResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.OK, "foo", ['content-type': 'application/json', 'content-length': 3])
        fullRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/keep-alive", ['connection': 'close']) | fullResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, "foo", ['content-type': 'application/json', 'content-length': 3, 'connection': 'close'])
    }

    private static FullHttpRequest fullRequest(HttpVersion version, HttpMethod method, String uri, Map<CharSequence, Object> headers = [:]) {
        def request = new DefaultFullHttpRequest(version, method, uri)
        for (Map.Entry<CharSequence, Object> entry : headers.entrySet()) {
            request.headers().add(entry.key, entry.value)
        }
        return request
    }

    private static FullHttpResponse fullResponse(HttpVersion version, HttpResponseStatus status, String body, Map<CharSequence, Object> headers) {
        def response = new DefaultFullHttpResponse(version, status, Unpooled.copiedBuffer(body, StandardCharsets.UTF_8))
        for (Map.Entry<CharSequence, Object> entry : headers.entrySet()) {
            response.headers().add(entry.key, entry.value)
        }
        return response
    }

    @Requires(property = "spec.name", value = "KeepAliveSpec")
    @Controller("/keep-alive")
    static class Ctrl {
        @Get
        String simple() {
            return "foo"
        }
    }
}
