package io.micronaut.http.server.netty.http2

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler
import io.netty.handler.codec.http2.Http2Error
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2GoAwayFrame
import io.netty.handler.codec.http2.Http2HeadersFrame
import io.netty.handler.codec.http2.Http2ResetFrame
import io.netty.handler.codec.http2.Http2StreamFrame
import io.netty.util.ReferenceCountUtil
import spock.lang.Specification

/**
 * A request whose HEADERS frame lacks a required pseudo-header is malformed (RFC 9113 §8.1.1).
 * It must be reset as a stream error, without failing the other streams of the connection.
 */
class Http2MalformedRequestHeadersSpec extends Specification {

    void "a request without the #missing pseudo-header resets only its own stream"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                    : 'Http2MalformedRequestHeadersSpec',
                'micronaut.server.http-version': '2.0',
        ])
        List<Object> frames = []
        def duplexHandler = new Http2ChannelDuplexHandler() {
            @Override
            void channelRead(ChannelHandlerContext ctx, Object msg) {
                frames.add(msg)
            }
        }
        def client = new EmbeddedChannel(Http2FrameCodecBuilder.forClient().build(), duplexHandler)

        def malformedStream = duplexHandler.newStream()
        def malformed = new DefaultHttp2Headers().scheme('http').authority('localhost')
        if (missing != 'method') {
            malformed.method('GET')
        }
        if (missing != 'path') {
            malformed.path('/h2malformed/slow')
        }
        client.writeOutbound(new DefaultHttp2HeadersFrame(malformed, true).stream(malformedStream))
        // another request, in flight on the same connection at the same time
        def otherStream = duplexHandler.newStream()
        def other = new DefaultHttp2Headers().method('GET').scheme('http').authority('localhost').path('/h2malformed/slow')
        client.writeOutbound(new DefaultHttp2HeadersFrame(other, true).stream(otherStream))

        ByteArrayOutputStream request = new ByteArrayOutputStream()
        ByteBuf outbound
        while ((outbound = client.readOutbound()) != null) {
            outbound.readBytes(request, outbound.readableBytes())
            outbound.release()
        }
        Socket socket = new Socket(server.host, server.port)
        socket.soTimeout = 3000

        when:
        socket.outputStream.write(request.toByteArray())
        socket.outputStream.flush()
        ByteArrayOutputStream response = new ByteArrayOutputStream()
        byte[] buf = new byte[8192]
        boolean closedByServer = false
        try {
            int n
            while ((n = socket.inputStream.read(buf)) != -1) {
                response.write(buf, 0, n)
            }
            closedByServer = true
        } catch (SocketTimeoutException ignored) {
            // the connection stays open
        }
        client.writeInbound(Unpooled.wrappedBuffer(response.toByteArray()))

        then: 'the malformed stream is reset with a stream error'
        frames.any { it instanceof Http2ResetFrame && it.stream().id() == malformedStream.id() && it.errorCode() == Http2Error.PROTOCOL_ERROR.code() }

        and: 'the other stream is answered and the connection is not failed'
        frames.any { it instanceof Http2HeadersFrame && it.stream().id() == otherStream.id() && it.headers().status().toString() == '200' }
        !frames.any { it instanceof Http2GoAwayFrame }
        !closedByServer

        cleanup:
        frames.each { ReferenceCountUtil.release(it) }
        socket?.close()
        client.finishAndReleaseAll()
        server.close()

        where:
        missing << ['path', 'method']
    }

    @Requires(property = 'spec.name', value = 'Http2MalformedRequestHeadersSpec')
    @Controller('/h2malformed')
    static class SlowController {
        @Get(uri = '/slow', produces = 'text/plain')
        String slow() {
            Thread.sleep(500)
            return 'ok'
        }
    }
}
