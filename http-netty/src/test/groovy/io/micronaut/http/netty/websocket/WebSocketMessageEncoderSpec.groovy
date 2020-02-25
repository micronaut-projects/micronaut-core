package io.micronaut.http.netty.websocket

import io.micronaut.http.MediaType
import io.netty.buffer.ByteBufUtil
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import spock.lang.Issue
import spock.lang.Specification

class WebSocketMessageEncoderSpec extends Specification {

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/2842")
    void "test a gstring is encoded correctly"() {
        WebSocketMessageEncoder encoder = new WebSocketMessageEncoder(null)

        when:
        WebSocketFrame frame = encoder.encodeMessage("${1+1}", MediaType.TEXT_PLAIN_TYPE)

        then:
        frame instanceof TextWebSocketFrame
        new String(ByteBufUtil.getBytes(frame.content())) == "2"
    }
}
