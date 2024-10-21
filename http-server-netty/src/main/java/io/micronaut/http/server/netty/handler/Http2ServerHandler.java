/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.handler;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.server.netty.HttpCompressionStrategy;
import io.micronaut.http.server.netty.handler.accesslog.Http2AccessLogConnectionEncoder;
import io.micronaut.http.server.netty.handler.accesslog.Http2AccessLogFrameListener;
import io.micronaut.http.server.netty.handler.accesslog.Http2AccessLogManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.HttpConversionUtil;

import java.nio.channels.ClosedChannelException;
import java.util.EnumMap;
import java.util.Map;

/**
 * HTTP/2-specific server handler.
 *
 * @since 4.4.0
 * @author Jonas Konrad
 */
@Internal
public final class Http2ServerHandler extends MultiplexedServerHandler implements Http2FrameListener {
    private static final Map<Http2Error, Exception> HTTP2_ERRORS = new EnumMap<>(Http2Error.class);

    private Http2ConnectionHandler connectionHandler;
    private Http2Connection.PropertyKey streamKey;
    private boolean reading = false;
    private boolean upgradedFromHttp1 = false;

    static {
        for (Http2Error value : Http2Error.values()) {
            Exception e;
            if (value == Http2Error.CANCEL) {
                e = StacklessStreamClosedChannelException.INSTANCE;
            } else {
                e = new StacklessHttp2Exception(value);
            }
            HTTP2_ERRORS.put(value, e);
        }
    }

    Http2ServerHandler(RequestHandler requestHandler) {
        super(requestHandler);
    }

    private void init(Http2ConnectionHandler connectionHandler) {
        this.connectionHandler = connectionHandler;
        streamKey = connectionHandler.connection().newKey();
    }

    @Override
    void flush() {
        // while reading, hold back flushes for efficiency.
        // Http2ConnectionHandler.readComplete does a flush.
        if (!reading) {
            connectionHandler.flush(ctx);
        }
    }

    @Override
    public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
        MultiplexedStream stream = connectionHandler.connection().stream(streamId).getProperty(streamKey);
        if (stream == null) {
            return padding; // data not consumed
        }
        return stream.onDataRead(data.retain(), endOfStream) + padding;
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding, boolean endOfStream) throws Http2Exception {
        onHeadersRead(ctx, streamId, headers, 0, Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, false, padding, endOfStream); // defaults from vertx
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endOfStream) throws Http2Exception {
        io.netty.handler.codec.http2.Http2Stream str = connectionHandler.connection().stream(streamId);
        Http2Stream stream = new Http2Stream(str);
        Http2Stream existing = str.setProperty(streamKey, stream);
        if (existing != null) {
            // ignore trailer and revert the setProperty. should not be hot path
            str.setProperty(streamKey, existing);
            return;
        }
        stream.onHeadersRead(HttpConversionUtil.toHttpRequest(streamId, headers, true), endOfStream);
    }

    @Override
    public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) {
        // frame deprecated by HTTP/2 spec
    }

    @Override
    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
        MultiplexedStream stream = connectionHandler.connection().stream(streamId).getProperty(streamKey);
        if (stream != null) {
            Http2Error http2Error = Http2Error.valueOf(errorCode);
            if (http2Error == null) {
                http2Error = Http2Error.INTERNAL_ERROR;
            }
            stream.onRstStreamRead(HTTP2_ERRORS.get(http2Error));
        }
    }

    @Override
    public void onSettingsAckRead(ChannelHandlerContext ctx) {
        // ignore
    }

    @Override
    public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
        // handled by netty (autoAckSettingsFrame=true)
    }

    @Override
    public void onPingRead(ChannelHandlerContext ctx, long data) {
        // handled by netty (autoAckPingFrame=true)
    }

    @Override
    public void onPingAckRead(ChannelHandlerContext ctx, long data) {
        // ignore
    }

    @Override
    public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) {
        // should not happen on server
    }

    @Override
    public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) throws Http2Exception {
        Http2Error http2Error = Http2Error.valueOf(errorCode);
        if (http2Error == null) {
            http2Error = Http2Error.INTERNAL_ERROR;
        }
        Exception e = HTTP2_ERRORS.get(http2Error);
        connectionHandler.connection().forEachActiveStream(s -> {
            Http2ServerHandler.Http2Stream stream = s.getProperty(streamKey);
            if (stream != null) {
                stream.onGoAwayRead(e);
            }
            return true;
        });
    }

    @Override
    public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) {
        // handled by netty
    }

    @Override
    public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags, ByteBuf payload) {
        // §5.5 "Implementations MUST discard frames that have unknown or unsupported types."
    }

    /**
     * {@link Http2ConnectionHandler} implementation containing the {@link Http2ServerHandler}.
     */
    public static final class ConnectionHandler extends Http2ConnectionHandler {
        private final Http2ServerHandler handler;
        @Nullable
        private final Http2AccessLogManager accessLogManager;

        private ConnectionHandler(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings, boolean decoupleCloseAndGoAway, boolean flushPreface, Http2ServerHandler handler, Http2AccessLogManager accessLogManager) {
            super(decoder, encoder, initialSettings, decoupleCloseAndGoAway, flushPreface);
            this.handler = handler;
            this.accessLogManager = accessLogManager;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            handler.ctx = ctx;
            super.handlerAdded(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            handler.reading = true;
            super.channelRead(ctx, msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            connection().forEachActiveStream(s -> {
                Http2ServerHandler.Http2Stream stream = s.getProperty(handler.streamKey);
                if (stream != null) {
                    stream.devolveToStreaming();
                }
                return true;
            });
            handler.reading = false;
            super.channelReadComplete(ctx);
        }

        @Override
        protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
            super.handlerRemoved0(ctx);
            connection().forEachActiveStream(s -> {
                Http2ServerHandler.Http2Stream stream = s.getProperty(handler.streamKey);
                if (stream != null) {
                    stream.onGoAwayRead(StacklessStreamClosedChannelException.INSTANCE);
                }
                return true;
            });
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof HttpServerUpgradeHandler.UpgradeEvent upgrade) {
                handler.upgradedFromHttp1 = true;
                FullHttpRequest fhr = upgrade.upgradeRequest();
                if (accessLogManager != null) {
                    accessLogManager.logHeaders(ctx, 1, fhr);
                }
                io.netty.handler.codec.http2.Http2Stream cs = connection().stream(1);
                handleFakeRequest(cs, fhr);
            }
            super.userEventTriggered(ctx, evt);
        }

        /**
         * Handle a request on the given stream that did not actually come in as an HTTP/2 request.
         * This is used for the h2c upgrade request which is an HTTP/1.1 request that expects an
         * HTTP/2 response, and for push promises where the request is initiated by the application.
         *
         * @param onStream The stream that the response should be sent on
         * @param fhr      The fake request
         */
        public void handleFakeRequest(io.netty.handler.codec.http2.Http2Stream onStream, FullHttpRequest fhr) {
            Http2Stream stream = handler.new Http2Stream(onStream);
            onStream.setProperty(handler.streamKey, stream);
            boolean empty = !fhr.content().isReadable();
            stream.onHeadersRead(fhr, empty);
            if (!empty) {
                stream.onDataRead(fhr.content(), true);
            }
        }
    }

    /**
     * {@link Http2ConnectionHandler} builder for the {@link Http2ServerHandler}.
     */
    public static final class ConnectionHandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<ConnectionHandler, ConnectionHandlerBuilder> {
        private final Http2ServerHandler frameListener;
        private Http2AccessLogManager.Factory accessLogManagerFactory;
        private Http2AccessLogManager accessLogManager;

        public ConnectionHandlerBuilder(RequestHandler requestHandler) {
            frameListener = new Http2ServerHandler(requestHandler);
        }

        @Override
        public ConnectionHandlerBuilder frameLogger(Http2FrameLogger frameLogger) {
            return super.frameLogger(frameLogger);
        }

        @Override
        public ConnectionHandlerBuilder validateHeaders(boolean validateHeaders) {
            return super.validateHeaders(validateHeaders);
        }

        @Override
        public ConnectionHandlerBuilder initialSettings(Http2Settings settings) {
            return super.initialSettings(settings);
        }

        public ConnectionHandlerBuilder accessLogManagerFactory(@Nullable Http2AccessLogManager.Factory accessLogManagerFactory) {
            this.accessLogManagerFactory = accessLogManagerFactory;
            return this;
        }

        public ConnectionHandlerBuilder compressor(HttpCompressionStrategy compressionStrategy) {
            if (compressionStrategy.isEnabled()) {
                frameListener.compressor(new Compressor(compressionStrategy));
            }
            return this;
        }

        public ConnectionHandlerBuilder bodySizeLimits(BodySizeLimits bodySizeLimits) {
            frameListener.bodySizeLimits = bodySizeLimits;
            return this;
        }

        @Override
        public ConnectionHandler build() {
            connection(new DefaultHttp2Connection(isServer(), maxReservedStreams()));
            Http2FrameListener fl = new DelegatingDecompressorFrameListener(connection(), frameListener, false);
            if (accessLogManagerFactory != null) {
                accessLogManager = new Http2AccessLogManager(accessLogManagerFactory, connection());
                fl = new Http2AccessLogFrameListener(fl, accessLogManager);
            }
            frameListener(fl);
            return super.build();
        }

        @Override
        protected ConnectionHandler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) throws Exception {
            if (accessLogManager != null) {
                encoder = new Http2AccessLogConnectionEncoder(encoder, accessLogManager);
            }
            ConnectionHandler ch = new ConnectionHandler(decoder, encoder, initialSettings, decoupleCloseAndGoAway(), flushPreface(), frameListener, accessLogManager);
            frameListener.init(ch);
            return ch;
        }
    }

    private final class Http2Stream extends MultiplexedStream {
        final io.netty.handler.codec.http2.Http2Stream stream;
        private boolean closeInput = false;

        Http2Stream(io.netty.handler.codec.http2.Http2Stream stream) {
            this.stream = stream;
        }

        @Override
        void notifyDataConsumed(int n) {
            if (stream.id() == 1 && upgradedFromHttp1) {
                // ignore for upgrade stream
                return;
            }
            try {
                connectionHandler.connection().local().flowController().consumeBytes(stream, n);
            } catch (Http2Exception e) {
                throw new IllegalArgumentException("n > unconsumedBytes", e);
            }
        }

        @Override
        boolean reset(Throwable cause) {
            if (cause instanceof Http2Exception h2e) {
                connectionHandler.encoder().writeRstStream(ctx, stream.id(), h2e.error().code(), ctx.voidPromise());
                return true;
            } else if (cause instanceof ByteBody.BodyDiscardedException) {
                connectionHandler.encoder().writeRstStream(ctx, stream.id(), Http2Error.CANCEL.code(), ctx.voidPromise());
                return true;
            } else {
                connectionHandler.encoder().writeRstStream(ctx, stream.id(), Http2Error.INTERNAL_ERROR.code(), ctx.voidPromise());
                return false;
            }
        }

        @Override
        void closeInput() {
            closeInput = true;
            if (stream.state() == io.netty.handler.codec.http2.Http2Stream.State.HALF_CLOSED_LOCAL) {
                connectionHandler.encoder().writeRstStream(ctx, stream.id(), Http2Error.CANCEL.code(), ctx.voidPromise());
                flush();
            }
        }

        @Override
        void writeHeaders(HttpResponse headers, boolean endStream, ChannelPromise promise) {
            if (endStream && closeInput) {
                promise = promise.unvoid();
                promise.addListener(future -> closeInput());
            }
            connectionHandler.encoder().writeHeaders(ctx, stream.id(), HttpConversionUtil.toHttp2Headers(headers, true), 0, endStream, promise);
        }

        @Override
        void writeData0(ByteBuf data, boolean endStream, ChannelPromise promise) {
            if (endStream && closeInput) {
                promise = promise.unvoid();
                promise.addListener(future -> closeInput());
            }
            connectionHandler.encoder().writeData(ctx, stream.id(), data, 0, endStream, promise);
        }
    }

    private static class StacklessStreamClosedChannelException extends ClosedChannelException {
        static final StacklessStreamClosedChannelException INSTANCE = new StacklessStreamClosedChannelException();

        @Override
        public String getMessage() {
            return "HTTP2 peer cancelled request stream";
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    private static class StacklessHttp2Exception extends Http2Exception {
        StacklessHttp2Exception(Http2Error error) {
            super(error, "HTTP/2 peer sent error: " + error);
        }

        @Override
        public Throwable fillInStackTrace() {
            // no stack trace
            return this;
        }
    }
}
