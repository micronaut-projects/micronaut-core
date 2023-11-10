package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class RequestCancelSpec extends Specification {
    def 'filter returning early response'() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name': 'RequestCancelSpec',
        ])
        def embeddedServer = (NettyHttpServer) ctx.getBean(EmbeddedServer)

        def serverEmbeddedChannel = embeddedServer.buildEmbeddedChannel(false)
        def clientEmbeddedChannel = new EmbeddedChannel()
        clientEmbeddedChannel.config().setAutoRead(true)

        EmbeddedTestUtil.connect(serverEmbeddedChannel, clientEmbeddedChannel)

        clientEmbeddedChannel.pipeline()
                .addLast(new HttpClientCodec())
                .addLast(new HttpObjectAggregator(1024))

        def request1 = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/early-response")
        request1.headers().set(HttpHeaderNames.CONTENT_LENGTH, 3)

        when:
        clientEmbeddedChannel.writeOneOutbound(request1)
        clientEmbeddedChannel.flushOutbound()
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)

        then:
        FullHttpResponse response1 = clientEmbeddedChannel.readInbound()
        response1.status() == HttpResponseStatus.UNAUTHORIZED

        when:
        clientEmbeddedChannel.writeOneOutbound(new DefaultLastHttpContent(Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8)))

        def request2 = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/ok")
        clientEmbeddedChannel.writeOneOutbound(request2)
        clientEmbeddedChannel.flushOutbound()
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)

        then:
        FullHttpResponse response2 = clientEmbeddedChannel.readInbound()
        response2.status() == HttpResponseStatus.OK

        cleanup:
        response1.release()
        response2.release()
        clientEmbeddedChannel.close()
        serverEmbeddedChannel.close()
        ctx.close()
    }

    @ServerFilter
    @Requires(property = "spec.name", value = "RequestCancelSpec")
    static class MyFilter {
        @RequestFilter("/early-response")
        HttpResponse<?> earlyResponse() {
            return HttpResponse.unauthorized()
        }
    }

    @Controller
    @Requires(property = "spec.name", value = "RequestCancelSpec")
    static class MyController {
        @Get("/ok")
        String ok() {
            return "ok"
        }
    }
}
