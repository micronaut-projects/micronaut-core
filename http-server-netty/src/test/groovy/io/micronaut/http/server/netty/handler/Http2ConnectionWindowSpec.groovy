package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.server.netty.EmbeddedTestUtil
import io.netty.buffer.ByteBuf
import io.netty.buffer.CompositeByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http2.Http2CodecUtil
import io.netty.handler.codec.http2.Http2ConnectionHandler
import io.netty.handler.codec.http2.Http2FrameCodec
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2FrameTypes
import io.netty.handler.codec.http2.Http2Settings
import io.netty.handler.codec.http2.Http2SettingsAckFrame
import io.netty.handler.codec.http2.Http2SettingsFrame
import spock.lang.Specification

class Http2ConnectionWindowSpec extends Specification {
    private static final int MIB = 1024 * 1024

    /**
     * Records the raw frames the client receives, before netty decodes them.
     */
    private static class FrameRecorder extends ChannelInboundHandlerAdapter {
        CompositeByteBuf received

        @Override
        void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            received = ctx.alloc().compositeBuffer()
        }

        @Override
        void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            received.release()
        }

        @Override
        void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            received.addComponent(true, ((ByteBuf) msg).retainedDuplicate())
            ctx.fireChannelRead(msg)
        }

        /**
         * @return The recorded frames as [type, streamId, windowSizeIncrement or null]
         */
        List<List<Integer>> frames() {
            List<List<Integer>> frames = []
            ByteBuf buf = received.duplicate()
            while (buf.readableBytes() >= Http2CodecUtil.FRAME_HEADER_LENGTH) {
                int length = buf.readUnsignedMedium()
                int type = buf.readUnsignedByte()
                buf.readUnsignedByte() // flags
                int streamId = buf.readInt() & Integer.MAX_VALUE
                assert buf.readableBytes() >= length
                Integer increment = null
                if (type == Http2FrameTypes.WINDOW_UPDATE) {
                    increment = buf.getInt(buf.readerIndex()) & Integer.MAX_VALUE
                }
                buf.skipBytes(length)
                frames.add([type, streamId, increment])
            }
            return frames
        }
    }

    private static class Harness {
        EmbeddedChannel server
        EmbeddedChannel client
        FrameRecorder recorder
        Http2FrameCodec clientCodec

        int clientConnectionWindow() {
            def connection = clientCodec.connection()
            return connection.remote().flowController().windowSize(connection.connectionStream())
        }
    }

    private static RequestHandler noopRequestHandler() {
        return new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }
    }

    private static Harness configure(Http2Settings settings, Integer connectionWindowSize, boolean legacy) {
        def harness = new Harness()
        harness.server = new EmbeddedChannel()
        harness.client = new EmbeddedChannel()
        EmbeddedTestUtil.connect(harness.server, harness.client)
        harness.recorder = new FrameRecorder()
        harness.clientCodec = Http2FrameCodecBuilder.forClient().build()
        harness.client.pipeline().addLast(harness.recorder, harness.clientCodec)
        // adding to the pipeline writes the http2 preface, so do it after connecting the server and client
        if (legacy) {
            Http2FrameCodec codec = Http2FrameCodecBuilder.forServer().initialSettings(settings).build()
            harness.server.pipeline().addLast(codec, new Http2ConnectionWindow(codec, Http2ConnectionWindow.effectiveWindowSize(settings, connectionWindowSize)))
        } else {
            harness.server.pipeline().addLast(new Http2ServerHandler.ConnectionHandlerBuilder(noopRequestHandler())
                    .initialSettings(settings)
                    .initialConnectionWindowSize(connectionWindowSize)
                    .build())
        }
        EmbeddedTestUtil.advance(harness.server, harness.client)
        return harness
    }

    private static void close(Harness harness) {
        harness.server.finishAndReleaseAll()
        harness.client.finishAndReleaseAll()
    }

    def "effective window size"(Integer streamWindow, Integer configured, int expected) {
        given:
        def settings = Http2Settings.defaultSettings()
        if (streamWindow != null) {
            settings.initialWindowSize(streamWindow)
        }

        expect:
        Http2ConnectionWindow.effectiveWindowSize(settings, configured) == expected

        where:
        streamWindow      | configured | expected
        null              | null       | 65535
        65535             | null       | 65535
        1000              | null       | 65535
        65536             | null       | 65537
        MIB               | null       | 65535 + 2 * (MIB - 65535)
        Integer.MAX_VALUE | null       | Integer.MAX_VALUE
        null              | 65535      | 65535
        null              | 100000     | 100000
        null              | 4 * MIB    | 4 * MIB
        MIB               | 4 * MIB    | 4 * MIB
        // the stream window rule is a floor, the configured value cannot go below it
        MIB               | 100000     | 65535 + 2 * (MIB - 65535)
        MIB               | MIB        | 65535 + 2 * (MIB - 65535)
    }

    def "connection window is left at the default when nothing is configured"(boolean legacy) {
        given:
        def harness = configure(Http2Settings.defaultSettings(), null, legacy)

        expect:
        harness.client.readInbound() instanceof Http2SettingsFrame
        harness.client.readInbound() instanceof Http2SettingsAckFrame
        harness.recorder.frames().findAll { it[0] == Http2FrameTypes.WINDOW_UPDATE }.isEmpty()
        harness.clientConnectionWindow() == Http2CodecUtil.DEFAULT_WINDOW_SIZE

        cleanup:
        close(harness)

        where:
        legacy << [false, true]
    }

    def "connection window follows the stream window by default"(boolean legacy) {
        given:
        def settings = Http2Settings.defaultSettings().initialWindowSize(MIB)
        def harness = configure(settings, null, legacy)
        int expectedDelta = 2 * (MIB - Http2CodecUtil.DEFAULT_WINDOW_SIZE)

        expect:
        harness.client.readInbound() instanceof Http2SettingsFrame
        harness.client.readInbound() instanceof Http2SettingsAckFrame
        // the SETTINGS preface must come first, then the WINDOW_UPDATE on stream 0
        harness.recorder.frames().take(2) == [
                [Http2FrameTypes.SETTINGS, 0, null],
                [Http2FrameTypes.WINDOW_UPDATE, 0, expectedDelta],
        ]
        harness.clientConnectionWindow() == Http2CodecUtil.DEFAULT_WINDOW_SIZE + expectedDelta

        cleanup:
        close(harness)

        where:
        legacy << [false, true]
    }

    def "explicit connection window is sent after the preface"(boolean legacy) {
        given:
        def harness = configure(Http2Settings.defaultSettings(), 4 * MIB, legacy)

        expect:
        harness.client.readInbound() instanceof Http2SettingsFrame
        harness.client.readInbound() instanceof Http2SettingsAckFrame
        harness.recorder.frames().take(2) == [
                [Http2FrameTypes.SETTINGS, 0, null],
                [Http2FrameTypes.WINDOW_UPDATE, 0, 4 * MIB - Http2CodecUtil.DEFAULT_WINDOW_SIZE],
        ]
        harness.clientConnectionWindow() == 4 * MIB

        cleanup:
        close(harness)

        where:
        legacy << [false, true]
    }

    def "small stream window increase is advertised right away"(boolean legacy) {
        given:
        // the legacy codec raises the refill size on its own, but does not send the update
        // for such a small increase
        def settings = Http2Settings.defaultSettings().initialWindowSize(70000)
        def harness = configure(settings, null, legacy)
        int expectedDelta = 2 * (70000 - Http2CodecUtil.DEFAULT_WINDOW_SIZE)

        expect:
        harness.client.readInbound() instanceof Http2SettingsFrame
        harness.client.readInbound() instanceof Http2SettingsAckFrame
        harness.recorder.frames().take(2) == [
                [Http2FrameTypes.SETTINGS, 0, null],
                [Http2FrameTypes.WINDOW_UPDATE, 0, expectedDelta],
        ]
        harness.recorder.frames().count { it[0] == Http2FrameTypes.WINDOW_UPDATE } == 1
        harness.clientConnectionWindow() == Http2CodecUtil.DEFAULT_WINDOW_SIZE + expectedDelta

        cleanup:
        close(harness)

        where:
        legacy << [false, true]
    }

    def "small explicit connection window is sent after the preface"(boolean legacy) {
        given:
        // less than double the default window: netty would only send the update once part
        // of the old window has been used
        def harness = configure(Http2Settings.defaultSettings(), 100000, legacy)

        expect:
        harness.client.readInbound() instanceof Http2SettingsFrame
        harness.client.readInbound() instanceof Http2SettingsAckFrame
        harness.recorder.frames().take(2) == [
                [Http2FrameTypes.SETTINGS, 0, null],
                [Http2FrameTypes.WINDOW_UPDATE, 0, 100000 - Http2CodecUtil.DEFAULT_WINDOW_SIZE],
        ]
        harness.recorder.frames().count { it[0] == Http2FrameTypes.WINDOW_UPDATE } == 1
        harness.clientConnectionWindow() == 100000

        cleanup:
        close(harness)

        where:
        legacy << [false, true]
    }

    def "explicit connection window wins over the stream window"(boolean legacy) {
        given:
        def settings = Http2Settings.defaultSettings().initialWindowSize(MIB)
        def harness = configure(settings, 4 * MIB, legacy)

        expect:
        harness.client.readInbound() instanceof Http2SettingsFrame
        harness.client.readInbound() instanceof Http2SettingsAckFrame
        harness.recorder.frames()[0] == [Http2FrameTypes.SETTINGS, 0, null]
        // the legacy codec sends its own update first, the second one tops it up
        harness.recorder.frames().findAll { it[0] == Http2FrameTypes.WINDOW_UPDATE && it[1] == 0 }*.get(2).sum() == 4 * MIB - Http2CodecUtil.DEFAULT_WINDOW_SIZE
        harness.clientConnectionWindow() == 4 * MIB

        cleanup:
        close(harness)

        where:
        legacy << [false, true]
    }

    def "stream window rule wins over a smaller explicit connection window"(boolean legacy) {
        given:
        def settings = Http2Settings.defaultSettings().initialWindowSize(MIB)
        def harness = configure(settings, MIB, legacy)
        int expectedDelta = 2 * (MIB - Http2CodecUtil.DEFAULT_WINDOW_SIZE)

        expect:
        harness.client.readInbound() instanceof Http2SettingsFrame
        harness.client.readInbound() instanceof Http2SettingsAckFrame
        harness.recorder.frames().take(2) == [
                [Http2FrameTypes.SETTINGS, 0, null],
                [Http2FrameTypes.WINDOW_UPDATE, 0, expectedDelta],
        ]
        harness.recorder.frames().count { it[0] == Http2FrameTypes.WINDOW_UPDATE } == 1
        harness.clientConnectionWindow() == Http2CodecUtil.DEFAULT_WINDOW_SIZE + expectedDelta

        cleanup:
        close(harness)

        where:
        legacy << [false, true]
    }

    def "legacy window handler removes itself"() {
        given:
        def harness = configure(Http2Settings.defaultSettings(), 4 * MIB, true)

        expect:
        harness.server.pipeline().get(Http2ConnectionWindow) == null
        harness.server.pipeline().get(Http2ConnectionHandler) != null

        cleanup:
        close(harness)
    }
}
