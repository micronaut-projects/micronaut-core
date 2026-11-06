package io.micronaut.http.server.netty.handler.accesslog

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http2.DefaultHttp2Connection
import io.netty.handler.codec.http2.DefaultHttp2ConnectionEncoder
import io.netty.handler.codec.http2.DefaultHttp2FrameWriter
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.Http2Connection
import io.netty.handler.codec.http2.Http2ConnectionEncoder
import io.netty.handler.codec.http2.Http2Headers
import io.netty.handler.codec.http2.Http2LifecycleManager
import org.slf4j.LoggerFactory
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

        cleanup:
        // the success path releases the buffer, but a regression that throws out of writeData
        // would leave it behind
        if (data.refCnt() > 0) {
            data.release(data.refCnt())
        }
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

    void "trailers end the logged response"() {
        given:
        Logger logger = (Logger) LoggerFactory.getLogger('Http2AccessLogConnectionEncoderSpec')
        logger.level = Level.INFO
        def listAppender = new ListAppender<ILoggingEvent>()
        listAppender.start()
        logger.addAppender(listAppender)
        Http2Connection connection = new DefaultHttp2Connection(true)
        Http2AccessLogManager manager = new Http2AccessLogManager(
                new Http2AccessLogManager.Factory(logger, '%r %s %b', null), connection)
        def logEncoder = new Http2AccessLogConnectionEncoder(new DefaultHttp2ConnectionEncoder(connection, new DefaultHttp2FrameWriter()), manager)
        logEncoder.lifecycleManager(Mock(Http2LifecycleManager))
        ChannelHandlerContext ctx = channel.pipeline().firstContext()
        logEncoder.flowController().channelHandlerContext(ctx)
        connection.remote().createStream(3, false)
        manager.logHeaders(ctx, 3, new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/trailers'))
        ByteBuf data = Unpooled.copiedBuffer("hello", StandardCharsets.UTF_8)

        when:
        logEncoder.writeHeaders(ctx, 3, new DefaultHttp2Headers().status("200"), 0, false, channel.newPromise())
        logEncoder.writeData(ctx, 3, data, 0, false, channel.newPromise())
        ChannelPromise promise = channel.newPromise()
        logEncoder.writeHeaders(ctx, 3, new DefaultHttp2Headers().add('grpc-status', '0'), 0, true, promise)
        // data and the trailers wait in the flow controller until the connection handler flushes
        logEncoder.flowController().writePendingBytes()
        channel.flush()

        then:
        noExceptionThrown()
        promise.isSuccess()
        listAppender.list*.formattedMessage == ['GET /trailers HTTP/2.0 200 5']

        cleanup:
        logger.detachAppender(listAppender)
    }
}
