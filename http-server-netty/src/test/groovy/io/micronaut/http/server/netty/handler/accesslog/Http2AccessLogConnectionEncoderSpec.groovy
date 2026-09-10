package io.micronaut.http.server.netty.handler.accesslog

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http2.DefaultHttp2Connection
import io.netty.handler.codec.http2.DefaultHttp2ConnectionEncoder
import io.netty.handler.codec.http2.DefaultHttp2FrameWriter
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.Http2Connection
import io.netty.handler.codec.http2.Http2ConnectionEncoder
import io.netty.handler.codec.http2.Http2Headers
import io.netty.handler.codec.http2.Http2LifecycleManager
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class Http2AccessLogConnectionEncoderSpec extends Specification {

    private EmbeddedChannel channel
    private Http2AccessLogConnectionEncoder encoder

    def setup() {
        Http2Connection connection = new DefaultHttp2Connection(true)
        Http2ConnectionEncoder delegate = new DefaultHttp2ConnectionEncoder(connection, new DefaultHttp2FrameWriter())
        Http2AccessLogManager manager = new Http2AccessLogManager(
                new Http2AccessLogManager.Factory(null, null, null), connection)
        encoder = new Http2AccessLogConnectionEncoder(delegate, manager)
        encoder.lifecycleManager(Mock(Http2LifecycleManager))
        channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter())
    }

    def cleanup() {
        channel?.finishAndReleaseAll()
    }

    void "writeData for a stream that is not in the connection fails the promise"() {
        given:
        ChannelHandlerContext ctx = channel.pipeline().firstContext()
        ByteBuf data = Unpooled.copiedBuffer("hello", StandardCharsets.UTF_8)
        ChannelPromise promise = channel.newPromise()

        when:
        encoder.writeData(ctx, 3, data, 0, true, promise)

        then:
        noExceptionThrown()
        promise.isDone()
        !promise.isSuccess()
        data.refCnt() == 0
    }

    void "writeHeaders for a stream that is not in the connection fails the promise"() {
        given:
        ChannelHandlerContext ctx = channel.pipeline().firstContext()
        Http2Headers headers = new DefaultHttp2Headers().status("200")
        ChannelPromise promise = channel.newPromise()

        when:
        encoder.writeHeaders(ctx, 3, headers, 0, true, promise)

        then:
        noExceptionThrown()
        promise.isDone()
        !promise.isSuccess()
    }
}
