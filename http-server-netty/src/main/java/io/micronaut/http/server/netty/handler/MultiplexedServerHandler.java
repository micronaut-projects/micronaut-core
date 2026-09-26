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
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.util.NativeImageUtils;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.netty.body.StreamingNettyByteBody;
import io.micronaut.http.netty.reactive.HotObservable;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.VoidChannelPromise;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http2.Http2Exception;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
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
    /**
     * Streaming responses that wrote data in the current event loop turn, see
     * {@link MultiplexedStream.ResponseStreamer#endBatch()}.
     */
    private final List<MultiplexedStream.ResponseStreamer> pendingBatches = new ArrayList<>();
    @Nullable
    private ChannelPromise silentVoidPromise;

    MultiplexedServerHandler(RequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }

    final void compressor(@Nullable Compressor compressor) {
        this.compressor = compressor;
    }

    /**
     * Flush the channel. Implementations may delay the flush to the end of the current event loop
     * turn, or to read complete while reading, and must call {@link #endTurn()} before the flush.
     */
    abstract void flush();

    /**
     * Finish the current event loop turn: the streaming responses that wrote data in this turn
     * hand their remaining piece to the channel. This must run before the flush that ends the
     * turn, so that the piece goes out with the rest of the data. It may write more data but does
     * not flush.
     */
    final void endTurn() {
        // endBatch may register a streamer again, e.g. when a write fails immediately and the
        // upstream reacts synchronously, so the size is re-read each iteration
        for (int i = 0; i < pendingBatches.size(); i++) {
            pendingBatches.get(i).endBatch();
        }
        pendingBatches.clear();
    }

    /**
     * A void promise that does not fire a failure down the pipeline, for the data frames of a
     * streaming response that are followed by another frame in the same turn. A failure that
     * concerns them also fails that later frame, which reports it.
     */
    private ChannelPromise silentVoidPromise() {
        ChannelPromise promise = silentVoidPromise;
        if (promise == null) {
            promise = new VoidChannelPromise(requiredCtx().channel(), false);
            silentVoidPromise = promise;
        }
        return promise;
    }

    private NettyByteBodyFactory byteBodyFactory() {
        return new NettyByteBodyFactory(requiredCtx().channel());
    }

    protected ChannelHandlerContext requiredCtx() {
        return Objects.requireNonNull(ctx);
    }

    /**
     * An HTTP/2 or HTTP/3 stream.
     */
    abstract class MultiplexedStream implements OutboundAccess {
        @Nullable
        private final Http2RequestEvent jfrEvent;
        @Nullable
        private HttpRequest request;
        @Nullable
        private List<ByteBuf> bufferedContent;
        private BufferConsumer.@Nullable Upstream writerUpstream;
        @Nullable
        private InputStreamer streamer;
        /**
         * The writer of the streaming response, once it is up for writing.
         */
        @Nullable
        private ResponseStreamer responseStreamer;

        @Nullable
        private Object attachment;

        private boolean requestAccepted;
        private boolean finished;
        private boolean reset;
        private boolean closed;
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
            if (requestAccepted) {
                throw new IllegalStateException("Request already accepted");
            }

            this.request = headers;
            if (endOfStream) {
                requestAccepted = true;
                requestHandler.accept(requiredCtx(), headers, NettyByteBodyFactory.empty(), this);
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
            if (streamer == null && closed) {
                // no request will be accepted for this stream anymore
                data.release();
            } else if (streamer == null) {
                if (requestAccepted) {
                    throw new IllegalStateException("Request already accepted");
                }

                if (endOfStream) {
                    // we got the full message before readComplete
                    ByteBuf fullBody;
                    if (bufferedContent == null) {
                        fullBody = data;
                    } else {
                        bufferedContent.add(data);
                        List<ByteBuf> pieces = bufferedContent;
                        // composeBody takes ownership of the pieces even when it fails
                        bufferedContent = null;
                        fullBody = PipeliningServerHandler.composeBody(requiredCtx().alloc(), pieces);
                    }
                    bufferedContent = null;

                    requestAccepted = true;
                    notifyDataConsumed(fullBody.readableBytes());
                    requestHandler.accept(requiredCtx(), Objects.requireNonNull(request), byteBodyFactory().createChecked(bodySizeLimits, fullBody), this);
                } else {
                    if (bufferedContent == null) {
                        bufferedContent = new ArrayList<>();
                    }
                    bufferedContent.add(data);
                }
            } else {
                streamer.add(byteBodyFactory().readBufferFactory().adapt(data));
                if (endOfStream) {
                    streamer.complete();
                }
            }
            return 0;
        }

        /**
         * Called on read complete. This makes the stream devolve into streaming mode, i.e. give up
         * on buffering data in hopes of reading it all in one go.
         */
        final void devolveToStreaming() {
            if (closed || requestAccepted || streamer != null || request == null) {
                return;
            }
            streamer = new InputStreamer(HttpUtil.is100ContinueExpected(request));
            // set the expected length before replaying the buffered frames, like the HTTP/1 path:
            // the declared Content-Length is charged to the size limit in full, and frames that
            // arrive without a known length are charged individually, so the buffered frames
            // would be counted twice if they were added first
            streamer.dest.setExpectedLengthFrom(request.headers());
            if (bufferedContent != null) {
                for (ByteBuf buf : bufferedContent) {
                    streamer.add(byteBodyFactory().readBufferFactory().adapt(buf));
                }
                bufferedContent = null;
            }
            requestAccepted = true;
            requestHandler.accept(requiredCtx(), request, new StreamingNettyByteBody(streamer.dest), this);
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
            reset = true;
            if (streamer != null) {
                streamer.error(e);
            }
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
            closed = true;
            if (bufferedContent != null) {
                for (ByteBuf buf : bufferedContent) {
                    buf.release();
                }
                bufferedContent = null;
            }
        }

        private void disposeWriteSide() {
            if (writerUpstream != null) {
                writerUpstream.allowDiscard();
                writerUpstream.disregardBackpressure();
            }
            if (responseStreamer != null) {
                responseStreamer.releaseHeld();
            }
            if (compressionSession != null) {
                compressionSession.discard();
            }
        }

        private boolean finish() {
            if (finished) {
                return false;
            }
            finished = true;
            disposeWriteSide();
            requestHandler.responseWritten(attachment);
            return true;
        }

        @Override
        public void write(HttpResponse response, ByteBody body) {
            body.touch();
            if (finished) {
                body.touch();
                // stream reset
                return;
            }

            // we do some preparation immediately on the calling thread, so that the ByteBody
            // primary operation happens here.

            response.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
            if (PipeliningServerHandler.canHaveBody(response.status())) {
                OptionalLong length = body.expectedLength();
                if (length.isPresent()) {
                    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, length.getAsLong());
                }
            } else {
                response.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
            }

            NettyByteBodyFactory byteBodyFactory = byteBodyFactory();
            if (body instanceof AvailableByteBody available) {
                writeFull(response, NettyByteBodyFactory.toByteBuf(available));
            } else {
                StreamingNettyByteBody snbb = byteBodyFactory.toStreaming(body);
                ResponseStreamer consumer = new ResponseStreamer(response, snbb.expectedLength().orElse(-1));
                // a body with data already available (e.g. a relayed client response) delivers it
                // from primary(). The writer holds it back until the response is opened, which
                // writes the HEADERS of the stream first.
                BufferConsumer.Upstream upstream = snbb.primary(consumer.writer);
                consumer.writer.attach(upstream);
                consumer.writer.execute(() -> consumer.open(upstream));
            }
        }

        @Override
        public void writeHeadResponse(HttpResponse response) {
            response.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
            writeFull(response, Unpooled.EMPTY_BUFFER);
        }

        private void writeFull(HttpResponse response, ByteBuf content) {
            if (finished) {
                content.release();
                return;
            } else if (reset) {
                // stream closed
                content.release();
                finish();
                return;
            }
            if (!requiredCtx().executor().inEventLoop()) {
                ByteBuf finalContent = content;
                requiredCtx().executor().execute(() -> writeFull(response, finalContent));
                return;
            }

            boolean empty = !content.isReadable();

            if (!empty) {
                prepareCompression(response, content.readableBytes());
            }

            if (compressionSession != null) {
                compressionSession.push(content);
                compressionSession.finish();
                compressionSession.fixContentLength(response);
                content = compressionSession.poll();
                empty = content == null;
            }

            writeHeaders(response, empty, empty ? endPromise(response) : requiredCtx().voidPromise());
            if (!empty) {
                // bypass writeDataCompressing
                writeData0(Objects.requireNonNull(content), true, endPromise(response));
            } else if (content != null) {
                content.release();
            }
            if (!finish()) {
                throw new IllegalStateException("Response already written");
            }
            flush();
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
        private class InputStreamer implements BufferConsumer.Upstream, BufferConsumer {
            final StreamingNettyByteBody.SharedBuffer dest = byteBodyFactory().createStreamingBuffer(bodySizeLimits, this);
            /**
             * Number of bytes that have been received by {@link #add(ReadBuffer)} but the downstream
             * hasn't consumed ({@link #onBytesConsumed(long)}). May be negative if the downstream
             * has signaled more consumption.
             */
            long unacknowledged = 0;
            boolean sendContinue;

            InputStreamer(boolean sendContinue) {
                this.sendContinue = sendContinue;
            }

            @Override
            public void start() {
                EventLoop eventLoop = requiredCtx().channel().eventLoop();
                if (!eventLoop.inEventLoop()) {
                    eventLoop.execute(this::start);
                    return;
                }

                if (sendContinue) {
                    writeHeaders(PipeliningServerHandler.ContinueOutboundHandler.CONTINUE_11, false, requiredCtx().voidPromise());
                    sendContinue = false;
                }
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

        /**
         * The HTTP/2 {@link StreamingResponseWriter.Sink} of a streaming response. The pieces of
         * the body that arrive in one event loop turn form a batch: all but the last piece are
         * written right away without a promise, the last one is held back until the end of the
         * turn ({@link #endBatch()}, which the handler calls before it flushes) and is written with
         * a single promise. A stream writes its frames in order, so that promise completes when the
         * whole batch has been written, and the upstream is told about the consumed bytes once per
         * batch instead of once per piece.
         */
        private final class ResponseStreamer implements StreamingResponseWriter.Sink {
            final HttpResponse response;
            final long contentLength;
            final StreamingResponseWriter writer = new StreamingResponseWriter(requiredCtx().channel().eventLoop(), this);
            /**
             * The last piece written in the current turn. Written by {@link #endBatch()}, or as
             * the final frame of the stream by the last {@link #write}.
             */
            @Nullable
            private ByteBuf held;
            /**
             * {@code true} iff this streamer is in {@link #pendingBatches}.
             */
            private boolean batchPending;

            ResponseStreamer(HttpResponse response, long contentLength) {
                this.response = response;
                this.contentLength = contentLength;
            }

            /**
             * Called on the event loop, in order with the body signals that arrived before, once
             * the body is subscribed to. The response is opened now: the writer holds back any
             * pieces that arrived before this, so the HEADERS of the stream go out first.
             */
            void open(BufferConsumer.Upstream upstream) {
                if (finished || reset) {
                    // the stream is gone (e.g. the connection closed) before the response could
                    // start. Disposing the writer reports responseWritten, which finishes the
                    // stream if that has not happened yet
                    upstream.allowDiscard();
                    upstream.disregardBackpressure();
                    writer.dispose();
                    return;
                }

                responseStreamer = this;
                writerUpstream = upstream;
                writer.open();
            }

            @Override
            public void open() {
                prepareCompression(response, contentLength);
                writeHeaders(response, false, requiredCtx().voidPromise());
            }

            @Override
            public void write(ByteBuf data, boolean last) {
                if (finished || reset) {
                    // the stream is gone: the upstream is told to discard when the stream is
                    // finished
                    data.release();
                    return;
                }
                ByteBuf previous = held;
                held = null;
                if (!last) {
                    held = data;
                    if (previous != null) {
                        // followed by the held piece in the same batch, which carries the promise
                        writeData(previous, false, silentVoidPromise());
                    }
                    if (!batchPending) {
                        batchPending = true;
                        pendingBatches.add(this);
                    }
                } else {
                    // the final bytes go out with the frame that ends the stream
                    if (previous != null) {
                        if (data.isReadable()) {
                            writeData(previous, false, silentVoidPromise());
                        } else {
                            data.release();
                            data = previous;
                        }
                    }
                    writeData(data, true, endPromise(response));
                }
                flush();
            }

            @Override
            public boolean isWritable() {
                // consumption is reported once the batch is written, see endBatch
                return false;
            }

            /**
             * Write the piece held back in this turn, with the promise for the whole batch.
             */
            void endBatch() {
                batchPending = false;
                ByteBuf data = held;
                held = null;
                long n = writer.takeUnconsumedBytes();
                if (data == null) {
                    return;
                }
                if (finished || reset) {
                    data.release();
                    return;
                }
                writeData(data, false, requiredCtx().newPromise()
                    .addListener((ChannelFutureListener) future -> batchWritten(future, n)));
            }

            private void batchWritten(ChannelFuture future, long n) {
                if (future.isSuccess()) {
                    writer.bytesConsumed(n);
                } else {
                    logStreamWriteFailure(future.cause());
                    writer.allowDiscard();
                }
            }

            void releaseHeld() {
                ByteBuf data = held;
                if (data != null) {
                    held = null;
                    data.release();
                }
            }

            @Override
            public void fail(Throwable e) {
                if (!reset(e)) {
                    LOG.warn("Reactive response received an error after some data has already been written. This error cannot be forwarded to the client.", e);
                }
                flush();
            }

            @Override
            public void responseWritten() {
                finish();
            }
        }
    }
}
