package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse as MicronautHttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.LastHttpContent
import reactor.core.publisher.Flux
import spock.lang.Specification

class NoContentStatusKeepAliveSpec extends Specification {

    def 'a #path response with a custom reason phrase has no framing headers and keeps an HTTP/1.1 connection alive'(String path, int code) {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'NoContentStatusKeepAliveSpec'])
        def server = ((NettyHttpServer) ctx.getBean(EmbeddedServer)).buildEmbeddedChannel(false)
        // no aggregator: it would add a content-length header of its own
        def client = new EmbeddedChannel(new HttpClientCodec())
        EmbeddedTestUtil.connect(server, client)

        when:
        client.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path))
        EmbeddedTestUtil.advance(server, client)
        HttpResponse first = readResponse(client)
        client.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path))
        EmbeddedTestUtil.advance(server, client)
        HttpResponse second = readResponse(client)

        then:
        first.status().code() == code
        !first.headers().contains(HttpHeaderNames.CONTENT_LENGTH)
        !first.headers().contains(HttpHeaderNames.TRANSFER_ENCODING)
        !first.headers().contains(HttpHeaderNames.CONNECTION)
        second.status().code() == code
        server.isOpen()

        cleanup:
        server.finishAndReleaseAll()
        client.finishAndReleaseAll()
        ctx.close()

        where:
        path                          | code
        '/no-content-status/full'     | 204
        '/no-content-status/stream'   | 204
        '/no-content-status/int'      | 204
        '/not-modified-status/full'   | 304
        '/not-modified-status/stream' | 304
    }

    private static HttpResponse readResponse(EmbeddedChannel client) {
        HttpResponse response = client.readInbound()
        Object last = client.readInbound()
        assert last instanceof LastHttpContent
        ((LastHttpContent) last).release()
        return response
    }

    @Requires(property = "spec.name", value = "NoContentStatusKeepAliveSpec")
    @Controller("/no-content-status")
    static class Ctrl {
        @Get("/full")
        MicronautHttpResponse<?> full() {
            return MicronautHttpResponse.status(HttpStatus.NO_CONTENT, "Nothing Here")
        }

        @Get("/stream")
        MicronautHttpResponse<Flux<String>> stream() {
            return MicronautHttpResponse.status(HttpStatus.NO_CONTENT, "Nothing Here").body(Flux.<String> empty())
        }

        @Get("/int")
        MicronautHttpResponse<?> intStatus() {
            return MicronautHttpResponse.status(204, "No Content")
        }
    }

    @Requires(property = "spec.name", value = "NoContentStatusKeepAliveSpec")
    @Controller("/not-modified-status")
    static class NotModifiedCtrl {
        @Get("/full")
        MicronautHttpResponse<?> full() {
            return MicronautHttpResponse.status(HttpStatus.NOT_MODIFIED, "Unchanged")
        }

        @Get("/stream")
        MicronautHttpResponse<Flux<String>> stream() {
            return MicronautHttpResponse.status(HttpStatus.NOT_MODIFIED, "Unchanged").body(Flux.<String> empty())
        }
    }
}
