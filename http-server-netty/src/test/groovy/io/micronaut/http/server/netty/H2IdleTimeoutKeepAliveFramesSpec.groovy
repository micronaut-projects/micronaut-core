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
 * Frames that do not move response data (connection WINDOW_UPDATEs that unblock nothing, PING
 * ACKs piling up behind a peer that stopped reading) must not keep a stalled HTTP/2 connection
 * open past the idle timeout.
 */
class H2IdleTimeoutKeepAliveFramesSpec extends Specification {

    void "connection WINDOW_UPDATEs do not keep a zero stream window connection open"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                    : 'H2IdleTimeoutKeepAliveFramesSpec',
                'micronaut.server.http-version': '2.0',
                'micronaut.server.idle-timeout': '2s',
        ])
        EmbeddedChannel encoder = new EmbeddedChannel(
                Http2FrameCodecBuilder.forClient().initialSettings(Http2Settings.defaultSettings().initialWindowSize(0)).build())
        5.times {
            def stream = encoder.pipeline().get(Http2FrameCodec).newStream()
            encoder.writeOutbound(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers().method('GET').scheme('http').authority('localhost').path('/keep-alive-frames-small'), true).stream(stream))
        }
        Socket socket = new Socket()
        socket.connect(new InetSocketAddress(server.host, server.port))
        socket.soTimeout = 100
        flush(encoder, socket.outputStream)
        // WINDOW_UPDATE, stream 0, increment 1
        byte[] windowUpdate = [0, 0, 4, 8, 0, 0, 0, 0, 0, 0, 0, 0, 1] as byte[]
        byte[] buf = new byte[16 * 1024]
        boolean closed = false

        when: "the client grants one byte of connection window every 500ms, but no stream window"
        long deadline = System.currentTimeMillis() + 10000
        long nextUpdate = 0
        while (System.currentTimeMillis() < deadline && !closed) {
            try {
                if (System.currentTimeMillis() >= nextUpdate) {
                    socket.outputStream.write(windowUpdate)
                    socket.outputStream.flush()
                    nextUpdate = System.currentTimeMillis() + 500
                }
                if (socket.inputStream.read(buf) == -1) {
                    closed = true
                }
            } catch (SocketTimeoutException ignored) {
            } catch (IOException ignored) {
                closed = true
            }
        }

        then:
        closed

        cleanup:
        socket?.close()
        encoder?.finishAndReleaseAll()
        server?.close()
    }

    void "PINGs from a peer that stopped reading do not keep the connection open"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                    : 'H2IdleTimeoutKeepAliveFramesSpec',
                'micronaut.server.http-version': '2.0',
                'micronaut.server.idle-timeout': '2s',
        ])
        EmbeddedChannel encoder = new EmbeddedChannel(
                Http2FrameCodecBuilder.forClient().initialSettings(Http2Settings.defaultSettings().initialWindowSize(Integer.MAX_VALUE)).build())
        def stream = encoder.pipeline().get(Http2FrameCodec).newStream()
        encoder.writeOutbound(new DefaultHttp2WindowUpdateFrame(Integer.MAX_VALUE - 65535))
        encoder.writeOutbound(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers().method('GET').scheme('http').authority('localhost').path('/keep-alive-frames-big'), true).stream(stream))
        Socket socket = new Socket()
        socket.receiveBufferSize = 4096
        socket.connect(new InetSocketAddress(server.host, server.port))
        socket.soTimeout = 200
        flush(encoder, socket.outputStream)

        when: "the client does not read, but sends a PING every 500ms"
        long deadline = System.currentTimeMillis() + 10000
        while (System.currentTimeMillis() < deadline) {
            encoder.writeOutbound(new DefaultHttp2PingFrame(System.nanoTime()))
            try {
                flush(encoder, socket.outputStream)
            } catch (IOException ignored) {
                break
            }
            sleep(500)
        }
        boolean closed = false
        byte[] buf = new byte[64 * 1024]
        socket.soTimeout = 5000
        long readDeadline = System.currentTimeMillis() + 20000
        long received = 0
        try {
            while (System.currentTimeMillis() < readDeadline) {
                int n = socket.inputStream.read(buf)
                received += Math.max(n, 0)
                if (n == -1) {
                    closed = true
                    break
                }
            }
        } catch (IOException ignored) {
            closed = true
        }

        then: "the server closed the connection before the whole response was written"
        closed
        received < 80 * 1024 * 1024

        cleanup:
        socket?.close()
        encoder?.finishAndReleaseAll()
        server?.close()
    }

    void "a response paced by WINDOW_UPDATEs alone stays open past the idle timeout"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                    : 'H2IdleTimeoutKeepAliveFramesSpec',
                'micronaut.server.http-version': '2.0',
                'micronaut.server.idle-timeout': '1s',
        ])
        EmbeddedChannel encoder = new EmbeddedChannel(
                Http2FrameCodecBuilder.forClient().initialSettings(Http2Settings.defaultSettings().initialWindowSize(16 * 1024)).build())
        def stream = encoder.pipeline().get(Http2FrameCodec).newStream()
        encoder.writeOutbound(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers().method('GET').scheme('http').authority('localhost').path('/keep-alive-frames-big'), true).stream(stream))
        Socket socket = new Socket()
        socket.connect(new InetSocketAddress(server.host, server.port))
        socket.soTimeout = 100
        flush(encoder, socket.outputStream)
        // WINDOW_UPDATE of 16 KiB on stream 0 and on stream 3 (1 is reserved for h2c upgrades)
        byte[] windowUpdates = [
                0, 0, 4, 8, 0, 0, 0, 0, 0, 0, 0, 0x40, 0,
                0, 0, 4, 8, 0, 0, 0, 0, 3, 0, 0, 0x40, 0] as byte[]
        byte[] buf = new byte[64 * 1024]
        boolean closed = false
        long received = 0

        when: "the client grants 16 KiB every 300ms for 4x the idle timeout, and sends nothing else"
        long deadline = System.currentTimeMillis() + 4000
        long nextUpdate = System.currentTimeMillis() + 300
        while (System.currentTimeMillis() < deadline && !closed) {
            try {
                if (System.currentTimeMillis() >= nextUpdate) {
                    socket.outputStream.write(windowUpdates)
                    socket.outputStream.flush()
                    nextUpdate = System.currentTimeMillis() + 300
                }
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
        }

        then:
        !closed
        received > 100 * 1024

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

    @Requires(property = 'spec.name', value = 'H2IdleTimeoutKeepAliveFramesSpec')
    @Controller
    static class BigController {
        @Get('/keep-alive-frames-small')
        String small() {
            'x' * 1024
        }

        @Get('/keep-alive-frames-big')
        String big() {
            'x' * (80 * 1024 * 1024)
        }
    }
}
