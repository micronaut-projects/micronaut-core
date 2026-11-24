package io.micronaut.http.server.netty.handler

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.server.netty.EmbeddedTestUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.compression.DecompressionException
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http2.DefaultHttp2Connection
import io.netty.handler.codec.http2.DefaultHttp2DataFrame
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler
import io.netty.handler.codec.http2.Http2Error
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2FrameStream
import io.netty.handler.codec.http2.Http2ResetFrame
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPOutputStream

/**
 * A request body that fails to decompress on HTTP/2 must fail the request body, like on HTTP/1,
 * so that the application can answer it, instead of resetting the stream.
 */
class Http2RequestDecompressionErrorSpec extends Specification {

    static class DuplexHandler extends Http2ChannelDuplexHandler {
        final List<Object> received = []

        @Override
        void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            received.add(msg)
        }
    }

    static class ListAppender extends AppenderBase<ILoggingEvent> {
        final List<ILoggingEvent> events = new CopyOnWriteArrayList<>()

        @Override
        protected void append(ILoggingEvent event) {
            events.add(event)
        }
    }

    EmbeddedChannel server
    EmbeddedChannel client
    DuplexHandler duplex
    List<Throwable> unboundErrors = []
    List<HttpRequest> requests = []
    List<CloseableByteBody> bodies = []
    List<OutboundAccess> outbound = []
    // netty logs, rather than propagates, failures of connection listeners such as the
    // decompressor cleanup
    Logger connectionLogger = (Logger) LoggerFactory.getLogger(DefaultHttp2Connection)
    ListAppender connectionLog = new ListAppender()

    def setup() {
        connectionLog.start()
        connectionLogger.addAppender(connectionLog)
        server = new EmbeddedChannel()
        client = new EmbeddedChannel()
        EmbeddedTestUtil.connect(server, client)
        server.pipeline().addLast(new Http2ServerHandler.ConnectionHandlerBuilder(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                requests.add(request)
                bodies.add(body)
                outbound.add(outboundAccess)
            }

            @Override
            void handleUnboundError(Throwable cause) {
                unboundErrors.add(cause)
            }
        }).decompress(true).build())
        duplex = new DuplexHandler()
        client.pipeline().addLast(Http2FrameCodecBuilder.forClient().build(), duplex)
        EmbeddedTestUtil.advance(server, client)
    }

    def cleanup() {
        connectionLogger.detachAppender(connectionLog)
        bodies.each { it.close() }
        server?.unsafe()?.close(server.unsafe().voidPromise())
        server?.runPendingTasks()
        server?.finishAndReleaseAll()
        client?.finishAndReleaseAll()
    }

    private Http2FrameStream sendHeaders() {
        def stream = duplex.newStream()
        def headers = new DefaultHttp2Headers()
        headers.method(HttpMethod.POST.asciiName()).scheme("http").authority("example.com").path("/")
        headers.add("content-encoding", "gzip")
        client.write(new DefaultHttp2HeadersFrame(headers, false).stream(stream))
        return stream
    }

    private int unconsumedConnectionBytes() {
        def connection = server.pipeline().get(Http2ServerHandler.ConnectionHandler).connection()
        return connection.local().flowController().unconsumedBytes(connection.connectionStream())
    }

    private static Throwable readFailure(CloseableByteBody body) {
        Throwable failure = null
        Flux.from(body.toByteArrayPublisher()).subscribe({}, { failure = it })
        return failure
    }

    private static byte[] gzip(byte[] data) {
        def bos = new ByteArrayOutputStream()
        new GZIPOutputStream(bos).withCloseable { it.write(data) }
        return bos.toByteArray()
    }

    def "invalid gzip in a single frame fails the request body"() {
        when:
        def stream = sendHeaders()
        client.write(new DefaultHttp2DataFrame(Unpooled.copiedBuffer('{"test":"invalid-gzip"}', StandardCharsets.UTF_8), true).stream(stream))
        client.flush()
        EmbeddedTestUtil.advance(server, client)

        then:
        server.checkException()
        unboundErrors.isEmpty()
        requests.size() == 1
        readFailure(bodies[0]) instanceof DecompressionException
        !duplex.received.any { it instanceof Http2ResetFrame }
        server.isOpen()
    }

    def "invalid gzip in a streaming body fails the body and drains the rest"() {
        when:
        def stream = sendHeaders()
        client.write(new DefaultHttp2DataFrame(Unpooled.copiedBuffer('this is not gzip at all', StandardCharsets.UTF_8), false).stream(stream))
        client.flush()
        EmbeddedTestUtil.advance(server, client)

        then:
        requests.size() == 1

        when:
        Throwable failure = null
        Flux.from(bodies[0].toByteArrayPublisher()).subscribe({}, { failure = it })
        byte[] rest = new byte[60000]
        client.write(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(rest), false).stream(stream))
        client.write(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(rest), true).stream(stream))
        client.flush()
        EmbeddedTestUtil.advance(server, client)

        then:
        server.checkException()
        unboundErrors.isEmpty()
        failure instanceof DecompressionException
        !duplex.received.any { it instanceof Http2ResetFrame }
        // the dropped data is returned to the connection window
        unconsumedConnectionBytes() == 0
        server.isOpen()
    }

    def "client reset in the middle of a gzip body does not fail cleanup"() {
        when:
        def stream = sendHeaders()
        byte[] gz = gzip(new byte[100000])
        client.write(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(gz, 0, gz.length.intdiv(2)), false).stream(stream))
        client.flush()
        EmbeddedTestUtil.advance(server, client)
        client.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(stream))
        EmbeddedTestUtil.advance(server, client)

        then:
        connectionLog.events.findAll { it.level.isGreaterOrEqual(Level.WARN) }.isEmpty()
        server.checkException()
        unboundErrors.isEmpty()
        server.isOpen()
    }

    def "valid gzip followed by garbage delivers the prefix, then fails the body"() {
        given:
        byte[] gz = gzip('{"test":"prefix"}'.getBytes(StandardCharsets.UTF_8))
        // drop the trailer (crc and length) and append data that is not deflate
        def bos = new ByteArrayOutputStream()
        bos.write(gz, 0, gz.length - 8)
        bos.write(('garbage' * 20).getBytes(StandardCharsets.UTF_8))
        byte[] broken = bos.toByteArray()

        when:
        def stream = sendHeaders()
        client.flush()
        EmbeddedTestUtil.advance(server, client)
        StringBuilder received = new StringBuilder()
        Throwable failure = null
        Flux.from(bodies[0].toByteArrayPublisher()).subscribe({ received.append(new String(it, StandardCharsets.UTF_8)) }, { failure = it })
        client.write(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(broken), false).stream(stream))
        client.flush()
        EmbeddedTestUtil.advance(server, client)
        client.write(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(new byte[1000]), true).stream(stream))
        client.flush()
        EmbeddedTestUtil.advance(server, client)

        then:
        server.checkException()
        failure instanceof DecompressionException
        received.toString() == '{"test":"prefix"}'
        !duplex.received.any { it instanceof Http2ResetFrame }
        unconsumedConnectionBytes() == 0
    }

    def "the client is told to stop sending once the error response is written"() {
        when:
        def stream = sendHeaders()
        client.write(new DefaultHttp2DataFrame(Unpooled.copiedBuffer('this is not gzip at all', StandardCharsets.UTF_8), false).stream(stream))
        client.flush()
        EmbeddedTestUtil.advance(server, client)
        Throwable failure = null
        Flux.from(bodies[0].toByteArrayPublisher()).subscribe({}, { failure = it })
        outbound[0].write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST), NettyByteBodyFactory.empty())
        server.runPendingTasks()
        server.flush()
        EmbeddedTestUtil.advance(server, client)

        then:
        failure instanceof DecompressionException
        def resets = duplex.received.findAll { it instanceof Http2ResetFrame }
        resets.size() == 1
        ((Http2ResetFrame) resets[0]).errorCode() == Http2Error.NO_ERROR.code()
        server.isOpen()
    }

    def "valid gzip still works"() {
        when:
        def stream = sendHeaders()
        client.write(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(gzip('{"test":"ok"}'.getBytes(StandardCharsets.UTF_8))), true).stream(stream))
        client.flush()
        EmbeddedTestUtil.advance(server, client)

        then:
        server.checkException()
        requests.size() == 1
        new String(Flux.from(bodies[0].toByteArrayPublisher()).reduce { a, b -> (a + b) as byte[] }.block(), StandardCharsets.UTF_8) == '{"test":"ok"}'
    }
}
