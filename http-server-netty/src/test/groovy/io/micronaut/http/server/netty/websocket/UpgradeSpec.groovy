package io.micronaut.http.server.netty.websocket

import io.micronaut.core.convert.ConversionService
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.server.HttpServerConfiguration
import io.micronaut.http.server.netty.NettyHttpRequest
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification
import spock.mock.DetachedMockFactory

class UpgradeSpec extends Specification {

    void "test websocket upgrade"() {
        given:
        final DefaultFullHttpRequest nettyRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/test")
        nettyRequest.headers().set(HttpHeaders.UPGRADE, "websocket")
        nettyRequest.headers().set(HttpHeaders.CONNECTION, "keep-alive, Upgrade")
        NettyServerWebSocketUpgradeHandler handler = new NettyServerWebSocketUpgradeHandler(null, null, null, null, null, null)

        when:
        HttpRequest<?> request = new NettyHttpRequest(
                nettyRequest,
                new DetachedMockFactory().Mock(ChannelHandlerContext.class),
                ConversionService.SHARED,
                new HttpServerConfiguration()
        )

        then:
        handler.acceptInboundMessage(request)
    }
}
