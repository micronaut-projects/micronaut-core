package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.ByteBuf
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.DefaultHttp2PingFrame
import io.netty.handler.codec.http2.DefaultHttp2WindowUpdateFrame
import io.netty.handler.codec.http2.Http2FrameCodec
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2Settings
import spock.lang.Specification

/**
 * A response that is still being written (the peer reads slowly, with enough flow-control
 * credit) is not closed by the idle check while the peer sends PINGs.
 */
class H2SlowReaderIdleTimeoutSpec extends Specification {

    static final int SIZE = 8 * 1024 * 1024

    void "a slowly read response is not closed while it makes write progress"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                    : 'H2SlowReaderIdleTimeoutSpec',
                'micronaut.server.http-version': '2.0',
                'micronaut.server.idle-timeout': '1s',
                // a small send buffer, so the server sees the slow reads as write progress. On Linux
                // loopback the default send buffer is sized from the 64KB MSS (about 2.6MB), and
                // epoll only reports the socket writable again once a third of it has drained,
                // which takes far longer than the idle timeout at the rate this client reads
                'micronaut.server.netty.childOptions.soSndbuf': 8192,
        ])
        // encode the client frames with netty, send them over a plain socket that we read slowly
        EmbeddedChannel encoder = new EmbeddedChannel(
                Http2FrameCodecBuilder.forClient().initialSettings(Http2Settings.defaultSettings().initialWindowSize(Integer.MAX_VALUE)).build())
        def stream = encoder.pipeline().get(Http2FrameCodec).newStream()
        encoder.writeOutbound(new DefaultHttp2WindowUpdateFrame(Integer.MAX_VALUE - 65535))
        encoder.writeOutbound(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers().method('GET').scheme('http').authority('localhost').path('/slow-reader-big'), true).stream(stream))
        Socket socket = new Socket()
        socket.receiveBufferSize = 4096
        socket.connect(new InetSocketAddress(server.host, server.port))
        socket.soTimeout = 200
        flush(encoder, socket.outputStream)
        long received = 0
        boolean closed = false
        byte[] buf = new byte[16 * 1024]

        when: "the client reads a little at a time and sends PINGs for 4x the idle timeout"
        long deadline = System.currentTimeMillis() + 4000
        while (System.currentTimeMillis() < deadline && !closed) {
            encoder.writeOutbound(new DefaultHttp2PingFrame(System.nanoTime()))
            try {
                flush(encoder, socket.outputStream)
                int n = socket.inputStream.read(buf)
                if (n == -1) {
                    closed = true
                } else {
                    received += n
                }
            } catch (SocketTimeoutException ignored) {
            } catch (IOException ignored) {
                closed = true
            }
            sleep(50)
        }

        then: "the response was still being written, and the connection stayed open"
        received > 0
        received < SIZE
        !closed

        cleanup:
        socket?.close()
        encoder?.finishAndReleaseAll()
        server?.close()
    }

    private static void flush(EmbeddedChannel encoder, OutputStream out) {
        ByteBuf b
        while ((b = encoder.readOutbound()) != null) {
            byte[] bytes = new byte[b.readableBytes()]
            b.readBytes(bytes)
            b.release()
            out.write(bytes)
        }
        out.flush()
    }

    @Requires(property = 'spec.name', value = 'H2SlowReaderIdleTimeoutSpec')
    @Controller
    static class BigController {
        @Get('/slow-reader-big')
        String big() {
            'x' * SIZE
        }
    }
}
