package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.CloseableByteBody
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http2.DefaultHttp2DataFrame
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler
import io.netty.handler.codec.http2.Http2Error
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2FrameStream
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * Request DATA frames that arrive before read complete are buffered, in the hope that the whole
 * body arrives in the same read. They hold a slice of the read buffer, so a stream that is closed
 * before read complete hands them to a request must release them.
 */
class Http2BufferedRequestDataSpec extends Specification {
    EmbeddedChannel server
    EmbeddedChannel client
    Http2ServerHandler.ConnectionHandler connectionHandler
    Http2ChannelDuplexHandler duplexHandler
    final List<CloseableByteBody> accepted = []
    final List<Throwable> unboundErrors = []

    def setup() {
        connectionHandler = new Http2ServerHandler.ConnectionHandlerBuilder(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                accepted.add(body)
            }

            @Override
            void handleUnboundError(Throwable cause) {
                unboundErrors.add(cause)
            }
        }).build()
        server = new EmbeddedChannel(connectionHandler)
        duplexHandler = new Http2ChannelDuplexHandler() {}
        client = new EmbeddedChannel(Http2FrameCodecBuilder.forClient().build(), duplexHandler)
    }

    def cleanup() {
        accepted.each { it.close() }
        server.finishAndReleaseAll()
        client.finishAndReleaseAll()
    }

    private Http2FrameStream writeRequestStart() {
        def stream = duplexHandler.newStream()
        def headers = new DefaultHttp2Headers()
        headers.method(HttpMethod.POST.asciiName())
        headers.scheme("http")
        headers.authority("example.com")
        headers.path("/")
        client.writeOutbound(new DefaultHttp2HeadersFrame(headers, false).stream(stream))
        client.writeOutbound(new DefaultHttp2DataFrame(Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8), false).stream(stream))
        return stream
    }

    /**
     * Deliver everything the client wrote as a single read, without the read complete. A single
     * contiguous buffer becomes the decoder's cumulation as is, so the buffered DATA frames are
     * slices of it and its reference count shows whether they were released.
     */
    private ByteBuf deliverAsOneRead() {
        ByteBuf read = Unpooled.buffer()
        ByteBuf msg
        while ((msg = client.readOutbound()) != null) {
            read.writeBytes(msg)
            msg.release()
        }
        server.writeOneInbound(read)
        server.checkException()
        return read
    }

    private int unconsumedConnectionBytes() {
        def connection = connectionHandler.connection()
        return connection.local().flowController().unconsumedBytes(connection.connectionStream())
    }

    def "buffered data is handed to the request body at read complete"() {
        given:
        writeRequestStart()

        when:
        def read = deliverAsOneRead()

        then: 'the data is held until read complete'
        read.refCnt() > 0
        accepted.isEmpty()

        when:
        server.pipeline().fireChannelReadComplete()
        server.checkException()

        then: 'the request body owns it now'
        accepted.size() == 1
        read.refCnt() > 0

        when:
        accepted.remove(0).close()

        then:
        read.refCnt() == 0
        unboundErrors.isEmpty()
    }

    def "buffered data is released when the client resets the stream in the same read"() {
        given:
        def stream = writeRequestStart()
        client.writeOutbound(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(stream))

        when:
        def read = deliverAsOneRead()

        then:
        read.refCnt() == 0
        unconsumedConnectionBytes() == 0

        when:
        server.pipeline().fireChannelReadComplete()
        server.checkException()

        then: 'the request is never accepted'
        accepted.isEmpty()
        unboundErrors.isEmpty()
        read.refCnt() == 0
    }

    def "buffered data is released when the connection is torn down before read complete: #description"() {
        given:
        writeRequestStart()
        if (goAway) {
            client.writeOutbound(new DefaultHttp2GoAwayFrame(Http2Error.CANCEL))
        }

        when:
        def read = deliverAsOneRead()

        then:
        read.refCnt() > 0

        when:
        tearDown.call(server)
        server.checkException()

        then:
        read.refCnt() == 0
        accepted.isEmpty()
        unboundErrors.isEmpty()

        where:
        description                   | goAway | tearDown
        'connection lost'             | false  | { EmbeddedChannel ch -> loseConnection(ch) }
        'goaway, then connection lost' | true   | { EmbeddedChannel ch -> loseConnection(ch) }
        'handler removed'             | false  | { EmbeddedChannel ch -> ch.pipeline().remove(Http2ServerHandler.ConnectionHandler) }
    }

    /**
     * Close the transport, like a peer that disconnects. {@code close()} on the channel would
     * start a graceful shutdown instead, which waits for the open stream.
     */
    private static void loseConnection(EmbeddedChannel ch) {
        ch.unsafe().close(ch.unsafe().voidPromise())
        ch.runPendingTasks()
    }
}
