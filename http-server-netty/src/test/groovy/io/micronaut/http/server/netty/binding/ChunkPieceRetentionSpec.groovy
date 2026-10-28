package io.micronaut.http.server.netty.binding

import io.micronaut.core.convert.DefaultConversionService
import io.micronaut.http.server.HttpServerConfiguration
import io.micronaut.http.server.netty.NettyHttpRequest
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * ANT-2026-NDS9POIN: a body that arrives in one-byte pieces must not be held as one buffer object
 * per piece, or a client chooses how much heap a limit-conforming body costs.
 */
class ChunkPieceRetentionSpec extends Specification {

    void 'a body received in one-byte pieces does not keep every piece'() {
        given:
        int pieces = 20_000
        EmbeddedChannel channel = new EmbeddedChannel()
        def ctx = channel.pipeline().addLast(new ChannelInboundHandlerAdapter()).firstContext()
        NettyHttpRequest request = new NettyHttpRequest(
                new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/"),
                ctx, new DefaultConversionService(), new HttpServerConfiguration())
        List<ByteBuf> received = []

        when:
        for (int i = 0; i < pieces; i++) {
            ByteBuf piece = Unpooled.buffer(1).writeByte((int) 'A')
            received.add(piece)
            def content = new DefaultHttpContent(piece)
            request.addContent(content)
            content.release()
        }
        long retained = received.count { it.refCnt() > 0 }
        ByteBuf body = (ByteBuf) request.getBody().get()

        then:
        retained <= 1000
        body.toString(StandardCharsets.US_ASCII) == 'A' * pieces

        cleanup:
        request.release()
        channel.finishAndReleaseAll()
    }
}
