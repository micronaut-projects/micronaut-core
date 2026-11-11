package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Head
import io.micronaut.http.annotation.Produces
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.ByteBuf
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.LastHttpContent
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class HeadResponseKeepAliveSpec extends Specification {

    def 'a HEAD response of #path has the Content-Length and keeps the connection usable'(String path, String length) {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'HeadResponseKeepAliveSpec'])
        def server = ((NettyHttpServer) ctx.getBean(EmbeddedServer)).buildEmbeddedChannel(false)
        def client = new EmbeddedChannel(new HttpClientCodec())
        EmbeddedTestUtil.connect(server, client)

        when:
        client.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.HEAD, path))
        EmbeddedTestUtil.advance(server, client)
        HttpResponse head = client.readInbound()
        Object headEnd = client.readInbound()
        client.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/head-keep-alive/plain'))
        EmbeddedTestUtil.advance(server, client)
        HttpResponse get = client.readInbound()
        StringBuilder body = new StringBuilder()
        Object part
        while ((part = client.readInbound()) != null) {
            body.append(((HttpContent) part).content().toString(StandardCharsets.UTF_8))
            ((HttpContent) part).release()
        }

        then:
        head.status().code() == 200
        head.headers().get(HttpHeaderNames.CONTENT_LENGTH) == length
        !head.headers().contains(HttpHeaderNames.CONNECTION)
        headEnd instanceof LastHttpContent
        !((LastHttpContent) headEnd).content().isReadable()
        get.status().code() == 200
        body.toString() == 'hello'
        server.isOpen()

        cleanup:
        if (headEnd instanceof LastHttpContent) {
            ((LastHttpContent) headEnd).release()
        }
        server.finishAndReleaseAll()
        client.finishAndReleaseAll()
        ctx.close()

        where:
        path                            | length
        '/head-keep-alive/explicit'     | '10'
        '/head-keep-alive/plain'        | '5'
    }

    @Requires(property = "spec.name", value = "HeadResponseKeepAliveSpec")
    @Controller("/head-keep-alive")
    static class Ctrl {
        @Head("/explicit")
        @Produces(MediaType.TEXT_PLAIN)
        String head() {
            return "hello-head"
        }

        @Get("/explicit")
        @Produces(MediaType.TEXT_PLAIN)
        String getExplicit() {
            return "hello-get"
        }

        @Get("/plain")
        @Produces(MediaType.TEXT_PLAIN)
        String plain() {
            return "hello"
        }
    }
}
