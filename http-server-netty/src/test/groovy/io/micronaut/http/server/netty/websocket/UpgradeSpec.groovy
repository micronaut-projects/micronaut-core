package io.micronaut.http.server.netty.websocket


import io.micronaut.http.HttpHeaders
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification
import spock.lang.Unroll

class UpgradeSpec extends Specification {

    @Unroll("#description")
    void "test websocket upgrade"(List<HeaderTuple> headers, String description) {
        given:
        final DefaultFullHttpRequest nettyRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/test")
        for (HeaderTuple header : headers) {
            nettyRequest.headers().set(header.name, header.value)
        }

        expect:
        NettyServerWebSocketUpgradeHandler.isWebSocketUpgrade(nettyRequest)

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
