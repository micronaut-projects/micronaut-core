package io.micronaut.http.server.netty.handler

import io.micronaut.core.io.buffer.ReadBuffer
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http2.DefaultHttp2DataFrame
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler
import io.netty.handler.codec.http2.Http2Error
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2FrameStream
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * A request body that is already streaming when its stream closes must be failed, so that its
 * consumer stops waiting and releases the data it holds.
 */
class Http2StreamingRequestBodySpec extends Specification {
    EmbeddedChannel server
    EmbeddedChannel client
    Http2ServerHandler.ConnectionHandler connectionHandler
    Http2ChannelDuplexHandler duplexHandler
    final List<CloseableByteBody> accepted = []
    final List<OutboundAccess> outbound = []
    final List<Throwable> unboundErrors = []

    def setup() {
        connectionHandler = new Http2ServerHandler.ConnectionHandlerBuilder(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                accepted.add(body)
                outbound.add(outboundAccess)
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

    private Http2FrameStream writeRequestStart(String contentLength = null) {
        def stream = duplexHandler.newStream()
        def headers = new DefaultHttp2Headers()
        headers.method(HttpMethod.POST.asciiName())
        headers.scheme("http")
        headers.authority("example.com")
        headers.path("/")
        if (contentLength != null) {
            headers.set("content-length", contentLength)
        }
        client.writeOutbound(new DefaultHttp2HeadersFrame(headers, false).stream(stream))
        client.writeOutbound(new DefaultHttp2DataFrame(Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8), false).stream(stream))
        return stream
    }

    private void writeData(Http2FrameStream stream, String data, boolean endStream) {
        client.writeOutbound(new DefaultHttp2DataFrame(Unpooled.copiedBuffer(data, StandardCharsets.UTF_8), endStream).stream(stream))
    }

    private ByteBuf deliverAsOneRead() {
        ByteBuf read = Unpooled.buffer()
        ByteBuf msg
        while ((msg = client.readOutbound()) != null) {
            read.writeBytes(msg)
            msg.release()
        }
        server.writeOneInbound(read)
        server.pipeline().fireChannelReadComplete()
        server.checkException()
        return read
    }

    private int unconsumedConnectionBytes() {
        def connection = connectionHandler.connection()
        return connection.local().flowController().unconsumedBytes(connection.connectionStream())
    }

    /**
     * Ways in which the stream of a request whose body is streaming can go away: the
     * description, the Content-Length of the request, and the action on the spec and stream.
     */
    private static final List<List<Object>> STREAM_GONE = [
            ['peer RST_STREAM', null, { Http2StreamingRequestBodySpec spec, Http2FrameStream s ->
                spec.client.writeOutbound(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(s))
                spec.deliverAsOneRead()
            }],
            ['peer RST_STREAM, then connection lost', null, { Http2StreamingRequestBodySpec spec, Http2FrameStream s ->
                spec.client.writeOutbound(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(s))
                spec.deliverAsOneRead()
                loseConnection(spec.server)
            }],
            ['peer GOAWAY, then connection lost', null, { Http2StreamingRequestBodySpec spec, Http2FrameStream s ->
                spec.client.writeOutbound(new DefaultHttp2GoAwayFrame(Http2Error.CANCEL))
                spec.deliverAsOneRead()
                loseConnection(spec.server)
            }],
            ['connection lost', null, { Http2StreamingRequestBodySpec spec, Http2FrameStream s ->
                loseConnection(spec.server)
            }],
            ['graceful close timed out', null, { Http2StreamingRequestBodySpec spec, Http2FrameStream s ->
                closeGracefullyAndTimeOut(spec.server)
            }],
            ['handler removed', null, { Http2StreamingRequestBodySpec spec, Http2FrameStream s ->
                spec.server.pipeline().remove(Http2ServerHandler.ConnectionHandler)
            }],
            ['more data than the Content-Length', '3', { Http2StreamingRequestBodySpec spec, Http2FrameStream s ->
                spec.writeData(s, "bar", false)
                spec.deliverAsOneRead()
            }],
            ['stream ends before the Content-Length', '10', { Http2StreamingRequestBodySpec spec, Http2FrameStream s ->
                spec.writeData(s, "ba", true)
                spec.deliverAsOneRead()
            }],
    ]

    def "a buffering request body is failed and released when its stream goes away: #description"() {
        given: 'a request whose body is being buffered by the application'
        def stream = writeRequestStart(contentLength)
        def read = deliverAsOneRead()
        CompletableFuture<?> buffered = accepted.remove(0).buffer()

        expect:
        !buffered.isDone()
        read.refCnt() > 0

        when:
        goAway.call(this, stream)
        server.checkException()

        then:
        buffered.isCompletedExceptionally()
        read.refCnt() == 0
        unboundErrors.isEmpty()

        where:
        [description, contentLength, goAway] << STREAM_GONE
    }

    def "a streaming request body is failed exactly once when its stream goes away: #description"() {
        given: 'a request whose body is being consumed by the application'
        def stream = writeRequestStart(contentLength)
        deliverAsOneRead()
        def subscriber = new RecordingSubscriber()
        accepted[0].toReadBufferPublisher().subscribe(subscriber)

        expect:
        subscriber.data.toString() == 'foo'

        when:
        goAway.call(this, stream)
        server.checkException()

        then:
        subscriber.errors == 1
        subscriber.completions == 0
        unboundErrors.isEmpty()

        where:
        [description, contentLength, goAway] << STREAM_GONE
    }

    def "a streaming request body that has ended is not failed when its stream closes: #description"() {
        given:
        def stream = writeRequestStart()
        def read = deliverAsOneRead()
        def subscriber = new RecordingSubscriber()
        accepted[0].toReadBufferPublisher().subscribe(subscriber)
        writeData(stream, "bar", true)
        def read2 = deliverAsOneRead()

        expect:
        subscriber.data.toString() == 'foobar'
        subscriber.completions == 1

        when:
        if (respond) {
            outbound[0].write(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyByteBodyFactory.empty())
            server.runPendingTasks()
        }
        loseConnection(server)
        server.checkException()

        then:
        subscriber.errors == 0
        subscriber.completions == 1
        read.refCnt() == 0
        read2.refCnt() == 0
        unboundErrors.isEmpty()

        where:
        description                                  | respond
        'response written, then connection lost'     | true
        'connection lost before the response'        | false
    }

    def "a request body discarded after an early response is released without errors"() {
        given:
        writeRequestStart()
        def read = deliverAsOneRead()

        when: 'the application answers without reading the body, which resets the stream with NO_ERROR'
        outbound[0].write(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED), NettyByteBodyFactory.empty())
        accepted[0].close()
        server.runPendingTasks()
        server.checkException()

        then:
        connectionHandler.connection().numActiveStreams() == 0
        read.refCnt() == 0
        unconsumedConnectionBytes() == 0
        unboundErrors.isEmpty()
    }

    def "a stream error on one request does not fail the body of another"() {
        given:
        def failing = writeRequestStart('3')
        def other = writeRequestStart()
        deliverAsOneRead()
        CompletableFuture<? extends CloseableByteBody> otherBody = accepted[1].buffer()

        when: 'the first request sends more data than its Content-Length'
        writeData(failing, "bar", false)
        deliverAsOneRead()

        then:
        !otherBody.isDone()

        when:
        writeData(other, "bar", true)
        deliverAsOneRead()

        then:
        otherBody.isDone()
        !otherBody.isCompletedExceptionally()
        unboundErrors.isEmpty()

        cleanup:
        otherBody.getNow(null)?.close()
    }

    /**
     * Close the transport, like a peer that disconnects. {@code close()} on the channel would
     * start a graceful shutdown instead, which waits for the open stream.
     */
    private static void loseConnection(EmbeddedChannel ch) {
        ch.unsafe().close(ch.unsafe().voidPromise())
        ch.runPendingTasks()
    }

    /**
     * Start a graceful shutdown, which waits for the open stream, and let it time out.
     * {@code EmbeddedChannel.close()} would cancel the timeout.
     */
    private static void closeGracefullyAndTimeOut(EmbeddedChannel ch) {
        ch.pipeline().close()
        ch.runPendingTasks()
        assert ch.isOpen()
        ch.advanceTimeBy(1, TimeUnit.HOURS)
        ch.runScheduledPendingTasks()
        ch.runPendingTasks()
        assert !ch.isOpen()
    }

    private static final class RecordingSubscriber implements Subscriber<ReadBuffer> {
        final StringBuilder data = new StringBuilder()
        int errors
        int completions

        @Override
        void onSubscribe(Subscription s) {
            s.request(Long.MAX_VALUE)
        }

        @Override
        void onNext(ReadBuffer buffer) {
            data.append(buffer.toString(StandardCharsets.UTF_8))
            buffer.close()
        }

        @Override
        void onError(Throwable t) {
            errors++
        }

        @Override
        void onComplete() {
            completions++
        }
    }
}
