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

import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.util.NativeImageUtils;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.netty.EventLoopFlow;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.netty.body.StreamingNettyByteBody;
import io.micronaut.http.netty.reactive.HotObservable;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http2.Http2Exception;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Common handler implementation for multiplexed HTTP versions (HTTP/2 and HTTP/3).
 *
 * @since 4.4.0
 * @author Jonas Konrad
 */
@Internal
abstract class MultiplexedServerHandler {
    final Logger LOG = LoggerFactory.getLogger(getClass());

    @Nullable
    ChannelHandlerContext ctx;
    BodySizeLimits bodySizeLimits = BodySizeLimits.UNLIMITED;
    private final RequestHandler requestHandler;
    @Nullable
    private Compressor compressor;

    MultiplexedServerHandler(RequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }

    final void compressor(@Nullable Compressor compressor) {
        this.compressor = compressor;
    }

    /**
     * Flush the channel.
     */
    abstract void flush();

    private NettyByteBodyFactory byteBodyFactory() {
        return new NettyByteBodyFactory(requiredCtx().channel());
    }

    protected ChannelHandlerContext requiredCtx() {
        return Objects.requireNonNull(ctx);
    }

    /**
     * Stateful inputs retain their payload after end or failure without another state allocation.
     */
    sealed interface InputState {
        InputPhase phase();
    }

    enum InputPhase implements InputState {
        AWAITING_HEADERS, HEADERS_RECEIVED, BUFFERING, STREAMING, ENDED, FAILED;

        @Override
        public InputPhase phase() {
            return this;
        }
    }

    /**
     * Replaces the buffered-pieces list without allocating a separate state wrapper. A failed
     * buffer keeps its pieces until close, when the transport settles the flow control window.
     */
    static final class Buffering extends ArrayList<ByteBuf> implements InputState {
        private InputPhase phase = InputPhase.BUFFERING;
        private long length;

        @Override
        public InputPhase phase() {
            return phase;
        }
    }

    enum OutputState {
        NOT_STARTED,
        /** Headers were handed to the connection, even if writing them failed. */
        HEADERS_WRITTEN,
        FINISHED
    }

    enum StreamEnd {
        OPEN, RESET, CLOSED
    }

    /**
     * An HTTP/2 or HTTP/3 stream.
     */
    @SuppressWarnings("checkstyle:MissingSwitchDefault") // List every input and output transition explicitly.
    abstract class MultiplexedStream implements OutboundAccess {
        @Nullable
        private final Http2RequestEvent jfrEvent;
        @Nullable
        private HttpRequest request;
        private BufferConsumer.@Nullable Upstream writerUpstream;
        private InputState input = InputPhase.AWAITING_HEADERS;
        private OutputState output = OutputState.NOT_STARTED;
        private StreamEnd end = StreamEnd.OPEN;

        @Nullable
        private Object attachment;

        private Compressor. @Nullable Session compressionSession;

        MultiplexedStream(int streamId) {
            if (NativeImageUtils.JFR_AVAILABLE && Http2RequestEvent.isTurnedOn()) {
                jfrEvent = new Http2RequestEvent();
                jfrEvent.streamId = streamId;
            } else {
                jfrEvent = null;
            }
        }

        /**
         * Called when the controller consumes some HTTP request data.
         *
         * @param n The number of bytes that have been consumed
         */
        abstract void notifyDataConsumed(int n);

        /**
         * Reset this stream.
         *
         * @param cause The exception that caused this stream to reset
         * @return {@code true} if this exception contained an error code and thus need not be
         * logged, {@code false} if it should be logged
         */
        abstract boolean reset(Throwable cause);

        /**
         * Close the input of the stream.
         */
        abstract void closeInput();

        /**
         * Called when the request headers are read.
         *
         * @param headers The headers
         * @param endOfStream Whether this is the last request packet
         */
        final void onHeadersRead(HttpRequest headers, boolean endOfStream) {
            switch (input.phase()) {
                case AWAITING_HEADERS -> {
                    request = headers;
                    if (endOfStream) {
                        input = InputPhase.ENDED;
                        requestHandler.accept(requiredCtx(), headers, NettyByteBodyFactory.empty(), this);
                    } else {
                        input = InputPhase.HEADERS_RECEIVED;
                    }
                }
                case HEADERS_RECEIVED, BUFFERING, STREAMING, ENDED -> throw new IllegalStateException("Request headers already received");
                case FAILED -> { }
            }
        }

        /**
         * Called when a data frame is read.
         *
         * @param data The input data. Release ownership is transferred to this method
         * @param endOfStream Whether this is the last request packet
         * @return The number of bytes that have been consumed immediately (like
         * {@link #notifyDataConsumed(int)})
         */
        final int onDataRead(ByteBuf data, boolean endOfStream) {
            switch (input.phase()) {
                case AWAITING_HEADERS -> {
                    data.release();
                    throw new IllegalStateException("Request headers not received");
                }
                case HEADERS_RECEIVED, BUFFERING -> {
                    Buffering buffering = input instanceof Buffering b ? b : null;
                    if (endOfStream) {
                        // we got the full message before readComplete
                        acceptFullBody(buffering, data);
                    } else if ((buffering == null ? 0 : buffering.length) + data.readableBytes() > bodySizeLimits.maxBufferSize()) {
                        // A decompressed body can exceed the flow control window: let the
                        // streaming buffer apply the size limits and backpressure.
                        devolveToStreaming();
                        if (input instanceof InputStreamer streamer) {
                            streamer.add(byteBodyFactory().readBufferFactory().adapt(data));
                        } else {
                            data.release();
                        }
                    } else {
                        if (buffering == null) {
                            buffering = new Buffering();
                            input = buffering;
                        }
                        buffering.add(data);
                        buffering.length += data.readableBytes();
                    }
                }
                case STREAMING, ENDED, FAILED -> {
                    if (input instanceof InputStreamer streamer) {
                        streamer.add(byteBodyFactory().readBufferFactory().adapt(data));
                        if (endOfStream) {
                            if (streamer.phase == InputPhase.STREAMING) {
                                streamer.phase = InputPhase.ENDED;
                            }
                            streamer.complete();
                        }
                    } else if (input == InputPhase.ENDED && end != StreamEnd.CLOSED) {
                        throw new IllegalStateException("Request already accepted");
                    } else {
                        // A reset before acceptance must never accept another body.
                        data.release();
                    }
                }
            }
            return 0;
        }

        /**
         * Accept the request with the buffered content and the given last piece as its full body.
         *
         * @param buffering The pieces received before the last piece, if any
         * @param data The last piece of the body. Release ownership is transferred to this method
         */
        private void acceptFullBody(@Nullable Buffering buffering, ByteBuf data) {
            ByteBuf fullBody;
            if (buffering == null) {
                fullBody = data;
            } else {
                if (data.isReadable()) {
                    buffering.add(data);
                } else {
                    data.release();
                }
                // composeBody takes ownership of the pieces even when it fails
                input = InputPhase.ENDED;
                fullBody = PipeliningServerHandler.composeBody(requiredCtx().alloc(), buffering);
            }
            input = InputPhase.ENDED;
            notifyDataConsumed(fullBody.readableBytes());
            requestHandler.accept(requiredCtx(), Objects.requireNonNull(request), byteBodyFactory().createChecked(bodySizeLimits, fullBody), this);
        }

        /**
         * Called when request trailers are read. The trailer values are not exposed to the
         * application, but the END_STREAM flag they carry completes the request body.
         *
         * @param endOfStream Whether this is the last request packet
         */
        final void onTrailersRead(boolean endOfStream) {
            if (!endOfStream) {
                return;
            }
            switch (input.phase()) {
                case AWAITING_HEADERS, ENDED, FAILED -> { }
                case HEADERS_RECEIVED -> acceptFullBody(null, Unpooled.EMPTY_BUFFER);
                case BUFFERING -> acceptFullBody((Buffering) input, Unpooled.EMPTY_BUFFER);
                case STREAMING -> {
                    InputStreamer streamer = (InputStreamer) input;
                    streamer.phase = InputPhase.ENDED;
                    streamer.complete();
                }
            }
        }

        /**
         * Called on read complete. This makes the stream devolve into streaming mode, i.e. give up
         * on buffering data in hopes of reading it all in one go.
         */
        final void devolveToStreaming() {
            switch (input.phase()) {
                case AWAITING_HEADERS, STREAMING, ENDED, FAILED -> { }
                case HEADERS_RECEIVED, BUFFERING -> {
                    HttpRequest headers = Objects.requireNonNull(request);
                    InputState previous = input;
                    InputStreamer streamer = new InputStreamer(HttpUtil.is100ContinueExpected(headers));
                    input = streamer;
                    // Set the expected length first so buffered frames are not charged twice.
                    streamer.dest.setExpectedLengthFrom(headers.headers());
                    if (previous instanceof Buffering buffering) {
                        for (ByteBuf buf : buffering) {
                            streamer.add(byteBodyFactory().readBufferFactory().adapt(buf));
                        }
                    }
                    requestHandler.accept(requiredCtx(), headers, new StreamingNettyByteBody(streamer.dest), this);
                }
            }
        }

        /**
         * Called on goaway.
         *
         * @param e The exception that should be forwarded to the stream consumer
         */
        final void onGoAwayRead(Exception e) {
            onRstStreamRead(e);
        }

        /**
         * Called on rst.
         *
         * @param e The exception that should be forwarded to the stream consumer
         */
        final void onRstStreamRead(Exception e) {
            if (end == StreamEnd.OPEN) {
                end = StreamEnd.RESET;
            }
            switch (input.phase()) {
                case AWAITING_HEADERS, HEADERS_RECEIVED -> input = InputPhase.FAILED;
                case BUFFERING -> ((Buffering) input).phase = InputPhase.FAILED;
                case STREAMING, ENDED -> {
                    if (input instanceof InputStreamer streamer) {
                        streamer.phase = InputPhase.FAILED;
                        streamer.error(e);
                    } else {
                        input = InputPhase.FAILED;
                    }
                }
                case FAILED -> { }
            }
            // The handler still owns an accepted request until it writes its response (#12332).
            disposeWriteSide();
        }

        /**
         * Called when the stream is closed, or when no more of it will be read. Request data
         * that is still buffered for the next read complete is released, and no request is
         * accepted for the stream afterwards. The released bytes are not reported to
         * {@link #notifyDataConsumed(int)}: the flow control window of a closed stream is settled
         * by the transport.
         */
        final void discardBufferedContent() {
            end = StreamEnd.CLOSED;
            switch (input.phase()) {
                case AWAITING_HEADERS, HEADERS_RECEIVED -> input = InputPhase.FAILED;
                case BUFFERING, FAILED -> {
                    if (input instanceof Buffering buffering) {
                        input = InputPhase.FAILED;
                        for (ByteBuf buf : buffering) {
                            buf.release();
                        }
                    }
                }
                case STREAMING -> {
                    InputStreamer streamer = (InputStreamer) input;
                    streamer.phase = InputPhase.FAILED;
                    streamer.error(new ClosedChannelException());
                    disposeWriteSide();
                }
                case ENDED -> { }
            }
            switch (output) {
                case NOT_STARTED, HEADERS_WRITTEN -> {
                    if (writerUpstream != null) {
                        // An idle streaming response may never signal again after close.
                        finish();
                    } else {
                        // A response written later finishes on the closed stream.
                        disposeWriteSide();
                    }
                }
                case FINISHED -> { }
            }
        }

        private void disposeWriteSide() {
            if (writerUpstream != null) {
                writerUpstream.allowDiscard();
                writerUpstream.disregardBackpressure();
            }
            if (compressionSession != null) {
                compressionSession.discard();
            }
        }

        private boolean finish() {
            assert requiredCtx().executor().inEventLoop();
            if (output == OutputState.FINISHED) {
                return false;
            }
            output = OutputState.FINISHED;
            disposeWriteSide();
            requestHandler.responseWritten(attachment);
            return true;
        }

        @Override
        public void write(HttpResponse response, ByteBody body) {
            body.touch();
            // note: if the stream is already finished (e.g. reset by the peer), writeFull and
            // writeStreaming release/cancel the body.

            // we do some preparation immediately on the calling thread, so that the ByteBody
            // primary operation happens here.

            response.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
            if (PipeliningServerHandler.canHaveBody(response.status())) {
                OptionalLong length = body.expectedLength();
                if (length.isPresent()) {
                    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, length.getAsLong());
                } else {
                    // the length is unknown, a header set by the application may not match the data
                    response.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
                }
            } else {
                response.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
            }

            NettyByteBodyFactory byteBodyFactory = byteBodyFactory();
            if (body instanceof AvailableByteBody available) {
                writeFull(response, NettyByteBodyFactory.toByteBuf(available));
            } else {
                StreamingNettyByteBody snbb = byteBodyFactory.toStreaming(body);
                var consumer = new BufferConsumer() {
                    @Nullable
                    Upstream upstream;
                    final EventLoopFlow flow = new EventLoopFlow(requiredCtx().channel().eventLoop());

                    @Override
                    public void add(ReadBuffer buf) {
                        if (flow.executeNow(() -> add0(buf))) {
                            add0(buf);
                        }
                    }

                    private void add0(ReadBuffer buf) {
                        if (output == OutputState.FINISHED) {
                            // the stream was closed while the response was streaming
                            buf.close();
                            return;
                        }
                        int n = buf.readable();
                        writeData(NettyReadBufferFactory.toByteBuf(buf), false, requiredCtx().newPromise()
                            .addListener((ChannelFutureListener) future -> {
                                if (future.isSuccess()) {
                                    Objects.requireNonNull(upstream).onBytesConsumed(n);
                                } else {
                                    logStreamWriteFailure(future.cause());
                                    Objects.requireNonNull(upstream).allowDiscard();
                                }
                            }));
                        flush();
                    }

                    @Override
                    public void complete() {
                        if (flow.executeNow(this::complete0)) {
                            complete0();
                        }
                    }

                    private void complete0() {
                        if (output != OutputState.FINISHED) {
                            // the end of stream frame uses a void promise, so it must not be
                            // written to a stream that netty has already closed
                            boolean open = end == StreamEnd.OPEN;
                            if (open) {
                                writeData(Unpooled.EMPTY_BUFFER, true, endPromise(response));
                            }
                            if (finish()) {
                                if (open) {
                                    flush();
                                }
                            }
                        }
                    }

                    @Override
                    public void error(Throwable e) {
                        if (flow.executeNow(() -> error0(e))) {
                            error0(e);
                        }
                    }

                    private void error0(Throwable e) {
                        if (!reset(e)) {
                            LOG.warn("Reactive response received an error after some data has already been written. This error cannot be forwarded to the client.", e);
                        }
                        finish();
                        flush();
                    }
                };
                consumer.upstream = snbb.primary(consumer);
                writeStreaming(response, consumer.upstream, snbb.expectedLength().orElse(-1));
            }
        }

        private void writeStreaming(HttpResponse response, BufferConsumer.Upstream upstream, long contentLength) {
            if (!requiredCtx().executor().inEventLoop()) {
                requiredCtx().executor().execute(() -> writeStreaming(response, upstream, contentLength));
                return;
            }

            switch (output) {
                case FINISHED -> {
                    upstream.allowDiscard();
                    upstream.disregardBackpressure();
                    return;
                }
                case NOT_STARTED, HEADERS_WRITTEN -> {
                    switch (end) {
                        case RESET, CLOSED -> {
                            upstream.allowDiscard();
                            upstream.disregardBackpressure();
                            finish();
                            return;
                        }
                        case OPEN -> {
                            if (output == OutputState.HEADERS_WRITTEN) {
                                upstream.allowDiscard();
                                upstream.disregardBackpressure();
                                abortWrittenResponse();
                                return;
                            }
                        }
                    }
                }
            }

            writerUpstream = upstream;

            prepareCompression(response, contentLength);

            output = OutputState.HEADERS_WRITTEN;
            writeHeaders(response, false, requiredCtx().voidPromise());
            upstream.start();
        }

        @Override
        public void writeHeadResponse(HttpResponse response) {
            if (!requiredCtx().executor().inEventLoop()) {
                requiredCtx().executor().execute(() -> writeHeadResponse(response));
                return;
            }
            response.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
            writeFull(response, Unpooled.EMPTY_BUFFER);
        }

        private void writeFull(HttpResponse response, ByteBuf content) {
            if (!requiredCtx().executor().inEventLoop()) {
                ByteBuf finalContent = content;
                requiredCtx().executor().execute(() -> writeFull(response, finalContent));
                return;
            }
            switch (output) {
                case FINISHED -> {
                    content.release();
                    return;
                }
                case NOT_STARTED, HEADERS_WRITTEN -> {
                    switch (end) {
                        case RESET, CLOSED -> {
                            content.release();
                            finish();
                            return;
                        }
                        case OPEN -> {
                            if (output == OutputState.HEADERS_WRITTEN) {
                                // A second HEADERS frame would be read as trailers.
                                content.release();
                                abortWrittenResponse();
                                return;
                            }
                        }
                    }
                }
            }

            // the content that is not handed to the compression or the connection yet
            ByteBuf owned = content;
            try {
                boolean empty = !content.isReadable();

                if (!empty) {
                    prepareCompression(response, content.readableBytes());
                }

                if (compressionSession != null) {
                    owned = null;
                    compressionSession.push(content);
                    compressionSession.finish();
                    compressionSession.fixContentLength(response);
                    content = compressionSession.poll();
                    owned = content;
                    empty = content == null;
                }

                output = OutputState.HEADERS_WRITTEN;
                writeHeaders(response, empty, empty ? endPromise(response) : requiredCtx().voidPromise());
                owned = null;
                if (!empty) {
                    // bypass writeDataCompressing
                    writeData0(Objects.requireNonNull(content), true, endPromise(response));
                } else if (content != null) {
                    content.release();
                }
            } catch (Throwable t) {
                if (owned != null) {
                    owned.release();
                }
                throw t;
            }
            if (!finish()) {
                throw new IllegalStateException("Response already written");
            }
            flush();
        }

        @Override
        public final void abort() {
            if (!requiredCtx().executor().inEventLoop()) {
                requiredCtx().executor().execute(this::abort);
                return;
            }
            switch (output) {
                case NOT_STARTED, HEADERS_WRITTEN -> abortWrittenResponse();
                case FINISHED -> { }
            }
        }

        /**
         * Reset the stream of a response that cannot be completed, and finish it.
         */
        private void abortWrittenResponse() {
            switch (end) {
                case OPEN -> reset(new IllegalStateException("The response could not be written"));
                case RESET, CLOSED -> { }
            }
            if (finish()) {
                flush();
            }
        }

        private ChannelPromise endPromise(HttpResponse response) {
            if (jfrEvent == null) {
                return requiredCtx().voidPromise();
            }
            return requiredCtx().newPromise().addListener((ChannelFutureListener) future -> {
                jfrEvent.end();
                if (jfrEvent.shouldCommit()) {
                    jfrEvent.populateChannel(requiredCtx().channel());
                    jfrEvent.populateRequest(Objects.requireNonNull(request));
                    jfrEvent.populateResponse(response);
                    jfrEvent.commit();
                }
            }).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }

        private void logStreamWriteFailure(Throwable cause) {
            if (cause instanceof Http2Exception h2e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Stream shut down by client while sending data", h2e);
                }
            } else {
                LOG.debug("Stream shut down by client while sending data", cause);
            }
        }

        @Override
        public final void attachment(Object attachment) {
            this.attachment = attachment;
        }

        @Override
        public final void closeAfterWrite() {
        }

        private void prepareCompression(HttpResponse headers, long contentLength) {
            if (compressor != null) {
                Compressor.Session session = compressor.prepare(requiredCtx(), Objects.requireNonNull(request), headers, contentLength);
                if (session != null) {
                    headers.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
                    compressionSession = session;
                }
            }
        }

        /**
         * Write the response headers.
         *
         * @param headers The response that should be transformed to headers
         * @param endStream Whether this is the last response frame
         * @param promise The promise to complete when the headers are written
         */
        abstract void writeHeaders(HttpResponse headers, boolean endStream, ChannelPromise promise);

        private void writeData(ByteBuf data, boolean endStream, ChannelPromise promise) {
            if (compressionSession == null) {
                writeData0(data, endStream, promise);
            } else {
                writeDataCompressing(compressionSession, data, endStream, promise);
            }
        }

        private void writeDataCompressing(Compressor.Session compressionChannel, ByteBuf data, boolean endStream, ChannelPromise promise) {
            compressionChannel.push(data);
            if (endStream) {
                compressionChannel.finish();
            }
            ByteBuf compressed = compressionChannel.poll();
            if (compressed == null) {
                if (endStream) {
                    writeData0(Unpooled.EMPTY_BUFFER, true, promise);
                } else {
                    promise.trySuccess();
                }
            } else {
                writeData0(compressed, endStream, promise);
            }
        }

        /**
         * Write response data.
         *
         * @param data The data bytes
         * @param endStream Whether this is the last response frame
         * @param promise The promise to complete when the data is written (used for backpressure)
         */
        abstract void writeData0(ByteBuf data, boolean endStream, ChannelPromise promise);

        /**
         * This is the {@link HotObservable} that represents the request body in the streaming
         * request case.
         */
        private final class InputStreamer implements InputState, BufferConsumer.Upstream, BufferConsumer {
            final StreamingNettyByteBody.SharedBuffer dest = byteBodyFactory().createStreamingBuffer(bodySizeLimits, this);
            /**
             * Number of bytes that have been received by {@link #add(ReadBuffer)} but the downstream
             * hasn't consumed ({@link #onBytesConsumed(long)}). May be negative if the downstream
             * has signaled more consumption.
             */
            long unacknowledged = 0;
            boolean sendContinue;
            private InputPhase phase = InputPhase.STREAMING;

            InputStreamer(boolean sendContinue) {
                this.sendContinue = sendContinue;
            }

            @Override
            public InputPhase phase() {
                return phase;
            }

            @Override
            public void start() {
                EventLoop eventLoop = requiredCtx().channel().eventLoop();
                if (!eventLoop.inEventLoop()) {
                    eventLoop.execute(this::start);
                    return;
                }

                if (sendContinue) {
                    sendContinue = false;
                    // an interim response is only allowed before the final response headers, and
                    // only on a stream that is still open. Writing it later fails in the encoder,
                    // which would take down the whole connection.
                    if (output == OutputState.NOT_STARTED && end == StreamEnd.OPEN) {
                        writeContinue();
                    }
                }
            }

            /**
             * Write the interim {@code 100 Continue} response and flush it. The body may be
             * subscribed to after the inbound read has completed (e.g. after an async step), in
             * which case no read-complete flush follows. {@link #flush()} still holds the flush
             * back while reading, so the in-read case is coalesced as before.
             */
            private void writeContinue() {
                writeHeaders(PipeliningServerHandler.ContinueOutboundHandler.CONTINUE_11, false, requiredCtx().voidPromise());
                flush();
            }

            @Override
            public void onBytesConsumed(long bytesConsumed) {
                if (bytesConsumed < 0) {
                    throw new IllegalArgumentException("Negative bytes consumed");
                }

                EventLoop eventLoop = requiredCtx().channel().eventLoop();
                if (!eventLoop.inEventLoop()) {
                    eventLoop.execute(() -> onBytesConsumed(bytesConsumed));
                    return;
                }

                long oldUnacknowledged = unacknowledged;
                if (oldUnacknowledged > 0) {
                    notifyDataConsumedLong(Math.min(bytesConsumed, oldUnacknowledged));
                }
                long newUnacknowledged = oldUnacknowledged - bytesConsumed;
                if (newUnacknowledged > oldUnacknowledged) {
                    // overflow, clamp
                    newUnacknowledged = Long.MIN_VALUE;
                }
                unacknowledged = newUnacknowledged;
            }

            private void notifyDataConsumedLong(long bytesConsumed) {
                if (bytesConsumed == 0) {
                    return;
                }
                assert bytesConsumed > 0;

                for (int i = 0; bytesConsumed > Integer.MAX_VALUE && i < 100; i++) {
                    notifyDataConsumed(Integer.MAX_VALUE);
                    bytesConsumed -= Integer.MAX_VALUE;
                }
                if (bytesConsumed > Integer.MAX_VALUE) {
                    LOG.debug("Clamping onBytesConsumed({})", bytesConsumed);
                    // so many bytes consumed at once, weird! just clamp.
                    bytesConsumed = Integer.MAX_VALUE;
                }
                notifyDataConsumed(Math.toIntExact(bytesConsumed));
                // flush any window updates
                flush();
            }

            @Override
            public void allowDiscard() {
                EventLoop eventLoop = requiredCtx().channel().eventLoop();
                if (!eventLoop.inEventLoop()) {
                    eventLoop.execute(this::allowDiscard);
                    return;
                }

                // the body will not be read, so do not ask the client to send it
                sendContinue = false;
                closeInput();
                dest.discard(); // signal discard
            }

            @Override
            public void disregardBackpressure() {
                EventLoop eventLoop = requiredCtx().channel().eventLoop();
                if (!eventLoop.inEventLoop()) {
                    eventLoop.execute(this::disregardBackpressure);
                    return;
                }

                unacknowledged = Long.MIN_VALUE;
            }

            @Override
            public void add(ReadBuffer buf) {
                assert requiredCtx().channel().eventLoop().inEventLoop();

                if (unacknowledged < 0) {
                    // -MIN_VALUE is still MIN_VALUE so we need to special case it
                    notifyDataConsumedLong(unacknowledged == Long.MIN_VALUE ? buf.readable() : Math.min(buf.readable(), -unacknowledged));
                }
                unacknowledged += buf.readable();
                dest.add(buf);
            }

            @Override
            public void complete() {
                dest.complete();
            }

            @Override
            public void discard() {
                // this is implemented in allowDiscard to reduce confusion about method names
                throw new UnsupportedOperationException();
            }

            @Override
            public void error(Throwable e) {
                dest.error(e);
            }
        }
    }
}
