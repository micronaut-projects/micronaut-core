package io.micronaut.http.netty.stream

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.LastHttpContent
import reactor.core.publisher.Flux
import spock.lang.Issue
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class HttpStreamsClientHandlerSpec extends Specification {
    @Issue('https://github.com/micronaut-projects/micronaut-tracing/issues/316')
    def 'out of order write'() {
        given:
        ChannelPromise firstWritePromise = null
        def channel = new EmbeddedChannel(
                new ChannelOutboundHandlerAdapter() {
                    @Override
                    void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                        // delay the completion of the write of the HttpRequest
                        if (firstWritePromise == null) {
                            firstWritePromise = promise
                            ctx.write(msg, ctx.voidPromise())
                            return
                        }
                        super.write(ctx, msg, promise)
                    }
                },
                new HttpStreamsClientHandler()
        )
        def msg = new DelegateStreamedHttpRequest(
                new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/"),
                JsonSubscriber.lift(Flux.just(new DefaultHttpContent(Unpooled.wrappedBuffer("\"foo\"".getBytes(StandardCharsets.UTF_8)))))
        )

        when:
        channel.writeOutbound(msg)
        firstWritePromise.trySuccess()
        channel.flushOutbound()
        then:
        channel.readOutbound() instanceof HttpRequest
        when:
        ByteBuf combined = Unpooled.buffer()
        while (true) {
            HttpContent h = channel.readOutbound()
            combined.writeBytes(h.content())
            if (h instanceof LastHttpContent) {
                break
            }
        }
        then:
        combined.toString(StandardCharsets.UTF_8) == "[\"foo\"]"
        !channel.finish()
    }
}
