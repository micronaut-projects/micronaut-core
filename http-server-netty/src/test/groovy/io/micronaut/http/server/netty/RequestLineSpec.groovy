package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import jakarta.inject.Singleton
import spock.lang.Issue
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class RequestLineSpec extends Specification {
    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/7193')
    def 'test different request lines http 1'() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name': 'RequestLineSpec',
        ])
        def embeddedServer = (NettyHttpServer) ctx.getBean(EmbeddedServer)

        def serverEmbeddedChannel = embeddedServer.buildEmbeddedChannel(false)
        def clientEmbeddedChannel = new EmbeddedChannel()
        clientEmbeddedChannel.config().setAutoRead(true)

        EmbeddedTestUtil.connect(serverEmbeddedChannel, clientEmbeddedChannel)

        clientEmbeddedChannel.pipeline()
                .addLast(new HttpClientCodec())
                .addLast(new HttpObjectAggregator(1024))

        def request1 = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
        request1.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)

        when:
        clientEmbeddedChannel.writeOneOutbound(request1)
        clientEmbeddedChannel.flushOutbound()
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)

        then:
        FullHttpResponse response = clientEmbeddedChannel.readInbound()
        response.status() == HttpResponseStatus.OK
        response.content().toString(StandardCharsets.UTF_8) == 'bar'

        cleanup:
        response.release()
        clientEmbeddedChannel.close()
        serverEmbeddedChannel.close()
        ctx.close()

        where:
        uri << [
                '/foo', // origin-form
                'http://example.com/foo', // absolute-form
                'http:///foo', // weird form
        ]
    }

    @Singleton
    @Controller
    @Requires(property = 'spec.name', value = 'RequestLineSpec')
    public static class SimpleController {
        @Get('/foo')
        public String foo() {
            return "bar"
        }
    }
}
