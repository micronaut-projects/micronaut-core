package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class RequestHttpVersionSpec extends Specification {
    def 'request http version matches request line #nettyVersion'() {
        given:
        ApplicationContext ctx = ApplicationContext.run(['spec.name': 'RequestHttpVersionSpec'])
        def embeddedServer = (NettyHttpServer) ctx.getBean(EmbeddedServer)
        def serverEmbeddedChannel = embeddedServer.buildEmbeddedChannel(false)
        def clientEmbeddedChannel = new EmbeddedChannel()
        clientEmbeddedChannel.config().setAutoRead(true)
        EmbeddedTestUtil.connect(serverEmbeddedChannel, clientEmbeddedChannel)
        clientEmbeddedChannel.pipeline()
                .addLast(new HttpClientCodec())
                .addLast(new HttpObjectAggregator(1024))

        when:
        clientEmbeddedChannel.writeOneOutbound(new DefaultFullHttpRequest(nettyVersion, HttpMethod.GET, '/http-version'))
        clientEmbeddedChannel.flushOutbound()
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)
        FullHttpResponse response = clientEmbeddedChannel.readInbound()

        then:
        response.status() == HttpResponseStatus.OK
        response.content().toString(StandardCharsets.UTF_8) == expected

        cleanup:
        response?.release()
        ctx.close()

        where:
        nettyVersion         | expected
        HttpVersion.HTTP_1_0 | 'HTTP_1_0'
        HttpVersion.HTTP_1_1 | 'HTTP_1_1'
    }

    @Requires(property = 'spec.name', value = 'RequestHttpVersionSpec')
    @Controller('/http-version')
    static class VersionController {
        @Get(produces = 'text/plain')
        String version(HttpRequest<?> request) {
            return request.httpVersion.name()
        }
    }
}
