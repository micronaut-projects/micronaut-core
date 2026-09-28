package io.micronaut.http.server.netty

import io.micronaut.http.netty.stream.HttpStreamsServerHandler
import io.micronaut.http.netty.stream.StreamedHttpRequest
import io.micronaut.http.netty.stream.StreamingInboundHttp2ToHttpAdapter
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http2.DefaultHttp2Connection
import io.netty.handler.codec.http2.DefaultHttp2DataFrame
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler
import io.netty.handler.codec.http2.Http2Connection
import io.netty.handler.codec.http2.Http2Error
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2FrameStream
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * Request DATA frames hold a slice of the read buffer. A stream that is closed before the request
 * body is read must release them.
 */
class Http2BufferedRequestDataSpec extends Specification {
    EmbeddedChannel server
    EmbeddedChannel client
    HttpToHttp2ConnectionHandler connectionHandler
    Http2ChannelDuplexHandler duplexHandler
    final List<HttpRequest> accepted = []

    def setup() {
        // the HTTP/2 part of the server pipeline, see HttpPipelineBuilder
        Http2Connection connection = new DefaultHttp2Connection(true)
        def adapter = new StreamingInboundHttp2ToHttpAdapter(connection, 10 * 1024 * 1024, true, true)
        connection.addListener(adapter)
        connectionHandler = new HttpToHttp2ConnectionHandlerBuilder()
                .frameListener(adapter)
                .connection(connection)
                .build()
        server = new EmbeddedChannel(
                connectionHandler,
                new MicronautFlowControlHandler(),
                new HttpStreamsServerHandler(),
                new ChannelInboundHandlerAdapter() {
                    @Override
                    void channelRead(ChannelHandlerContext ctx, Object msg) {
                        // like a route that has not read the body yet
                        accepted.add((HttpRequest) msg)
                    }
                })
        duplexHandler = new Http2ChannelDuplexHandler() {}
        client = new EmbeddedChannel(Http2FrameCodecBuilder.forClient().build(), duplexHandler)
    }

    def cleanup() {
        accepted.each { discard(it) }
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
     * Deliver everything the client wrote as a single read. A single contiguous buffer becomes the
     * decoder's cumulation as is, so the DATA frames are slices of it and its reference count shows
     * whether they were released.
     */
    private ByteBuf deliverAsOneRead() {
        ByteBuf read = Unpooled.buffer()
        ByteBuf msg
        while ((msg = client.readOutbound()) != null) {
            read.writeBytes(msg)
            msg.release()
        }
        server.writeOneInbound(read)
        server.pipeline().fireChannelReadComplete()
        server.runPendingTasks()
        server.checkException()
        return read
    }

    /**
     * Read the body of the request to the end, releasing each piece.
     */
    private static void discard(HttpRequest request) {
        if (request instanceof StreamedHttpRequest) {
            ((StreamedHttpRequest) request).subscribe(new Subscriber<HttpContent>() {
                @Override
                void onSubscribe(Subscription s) {
                    s.request(Long.MAX_VALUE)
                }

                @Override
                void onNext(HttpContent httpContent) {
                    httpContent.release()
                }

                @Override
                void onError(Throwable t) {
                }

                @Override
                void onComplete() {
                }
            })
        }
    }

    def "the request body owns the data until it is read"() {
        given:
        writeRequestStart()

        when:
        def read = deliverAsOneRead()

        then:
        accepted.size() == 1
        read.refCnt() > 0

        when:
        discard(accepted.remove(0))
        server.runPendingTasks()

        then:
        read.refCnt() == 0
    }

    def "unread data is released when the client resets the stream in the same read"() {
        given:
        def stream = writeRequestStart()
        client.writeOutbound(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(stream))

        when:
        def read = deliverAsOneRead()

        then:
        read.refCnt() == 0
    }

    def "unread data is released when the client resets the stream later"() {
        given:
        def stream = writeRequestStart()

        when:
        def read = deliverAsOneRead()

        then:
        read.refCnt() > 0

        when:
        client.writeOutbound(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(stream))
        deliverAsOneRead()

        then:
        read.refCnt() == 0
    }

    def "unread data is released when the connection is torn down: #description"() {
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

        where:
        description                    | goAway | tearDown
        'connection lost'              | false  | { EmbeddedChannel ch -> loseConnection(ch) }
        'goaway, then connection lost' | true   | { EmbeddedChannel ch -> loseConnection(ch) }
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
