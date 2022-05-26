package io.micronaut.http.server.netty.websocket

import io.micronaut.core.convert.ConversionService
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.netty.websocket.WebSocketSessionRepository
import io.micronaut.http.server.HttpServerConfiguration
import io.micronaut.http.server.binding.RequestArgumentSatisfier
import io.micronaut.http.server.netty.NettyEmbeddedServices
import io.micronaut.http.server.netty.NettyHttpRequest
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification
import spock.lang.Unroll
import spock.mock.DetachedMockFactory

class UpgradeSpec extends Specification {

    @Unroll("#description")
    void "test websocket upgrade"(List<HeaderTuple> headers, String description) {
        given:
        final DefaultFullHttpRequest nettyRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/test")
        for (HeaderTuple header : headers) {
            nettyRequest.headers().set(header.name, header.value)
        }

        def mock = Mock(NettyEmbeddedServices)
        mock.getRequestArgumentSatisfier() >> new RequestArgumentSatisfier(null)
        NettyServerWebSocketUpgradeHandler handler = new NettyServerWebSocketUpgradeHandler(mock, Mock(WebSocketSessionRepository))

        when:
        HttpRequest<?> request = new NettyHttpRequest(
                nettyRequest,
                new DetachedMockFactory().Mock(ChannelHandlerContext.class),
                ConversionService.SHARED,
                new HttpServerConfiguration()
        )

        then:
        handler.acceptInboundMessage(request)

        where:
        headers << [
                [new HeaderTuple(HttpHeaders.UPGRADE, "websocket"), new HeaderTuple(HttpHeaders.CONNECTION, "upgrade")],
                [new HeaderTuple(HttpHeaders.UPGRADE, "Websocket"), new HeaderTuple(HttpHeaders.CONNECTION, "Upgrade")],
                [new HeaderTuple(HttpHeaders.UPGRADE, "websocket"), new HeaderTuple(HttpHeaders.CONNECTION, "Upgrade")],
                [new HeaderTuple(HttpHeaders.UPGRADE, "websocket"), new HeaderTuple(HttpHeaders.CONNECTION, "keep-alive, Upgrade")],
                [new HeaderTuple("upgrade", "websocket"), new HeaderTuple("connection", "keep-alive, Upgrade")]
        ]
        description = "For headers ${headers.toListString()} NettyServerWebSocketUpgradeHandler::acceptInboundMessage returns true"
    }

    static class HeaderTuple {
        String name
        String value

        HeaderTuple(String name, String value) {
            this.name = name
            this.value = value
        }

        @Override
        String toString() {
            "$name = $value"
        }
    }
}
