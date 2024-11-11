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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.netty.EventLoopFlow;
import io.micronaut.http.netty.body.AvailableNettyByteBody;
import io.micronaut.http.netty.body.ByteBufConsumer;
import io.micronaut.http.netty.body.NettyBodyAdapter;
import io.micronaut.http.netty.body.NettyByteBody;
import io.micronaut.http.netty.body.StreamingNettyByteBody;
import io.micronaut.http.netty.reactive.HotObservable;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * An HTTP/2 or HTTP/3 stream.
     */
    abstract class MultiplexedStream implements OutboundAccess {
        private HttpRequest request;

        private List<ByteBuf> bufferedContent;
        private BufferConsumer.Upstream writerUpstream;
        private InputStreamer streamer;

        private Object attachment;

        private boolean requestAccepted;
        private boolean responseDone;
        private Compressor.Session compressionSession;

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
                requestHandler.accept(ctx, headers, AvailableNettyByteBody.empty(), this);
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
            if (streamer == null) {
                if (requestAccepted) {
                    throw new IllegalStateException("Request already accepted");
                }

                if (endOfStream) {
                    // we got the full message before readComplete
                    ByteBuf fullBody;
                    if (bufferedContent == null) {
                        fullBody = data;
                    } else {
                        CompositeByteBuf composite = ctx.alloc().compositeBuffer();
                        for (ByteBuf c : bufferedContent) {
                            composite.addComponent(true, c);
                        }
                        composite.addComponent(true, data);
                        fullBody = composite;
                    }
                    bufferedContent = null;

                    requestAccepted = true;
                    notifyDataConsumed(fullBody.readableBytes());
                    requestHandler.accept(ctx, request, AvailableNettyByteBody.createChecked(ctx.channel().eventLoop(), bodySizeLimits, fullBody), this);
                } else {
                    if (bufferedContent == null) {
                        bufferedContent = new ArrayList<>();
                    }
                    bufferedContent.add(data);
                }
            } else {
                streamer.add(data);
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
            if (requestAccepted || streamer != null || request == null) {
                return;
            }
            streamer = new InputStreamer(HttpUtil.is100ContinueExpected(request));
            if (bufferedContent != null) {
                for (ByteBuf buf : bufferedContent) {
                    streamer.add(buf);
                }
                bufferedContent = null;
            }
            requestAccepted = true;
            streamer.dest.setExpectedLengthFrom(request.headers());
            requestHandler.accept(ctx, request, new StreamingNettyByteBody(streamer.dest), this);
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
            if (streamer != null) {
                streamer.error(e);
            }
            finish();
        }

        private boolean finish() {
            if (responseDone) {
                return false;
            }
            responseDone = true;
            if (writerUpstream != null) {
                writerUpstream.allowDiscard();
                writerUpstream.disregardBackpressure();
            }
            if (compressionSession != null) {
                compressionSession.discard();
            }
            requestHandler.responseWritten(attachment);
            return true;
        }

        @Override
        public void write(@NonNull HttpResponse response, @NonNull ByteBody body) {
            if (responseDone) {
                // early check
                throw new IllegalStateException("Response already written");
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

            NettyByteBody nbb = NettyBodyAdapter.adapt(body, ctx.channel().eventLoop());
            if (nbb instanceof AvailableNettyByteBody available) {
                writeFull(response, AvailableNettyByteBody.toByteBuf(available));
            } else {
                StreamingNettyByteBody snbb = (StreamingNettyByteBody) nbb;
                var consumer = new ByteBufConsumer() {
                    Upstream upstream;
                    final EventLoopFlow flow = new EventLoopFlow(ctx.channel().eventLoop());

                    @Override
                    public void add(ByteBuf buf) {
                        if (flow.executeNow(() -> add0(buf))) {
                            add0(buf);
                        }
                    }

                    private void add0(ByteBuf buf) {
                        int n = buf.readableBytes();
                        writeData(buf, false, ctx.newPromise()
                            .addListener((ChannelFutureListener) future -> {
                                if (future.isSuccess()) {
                                    upstream.onBytesConsumed(n);
                                } else {
                                    logStreamWriteFailure(future.cause());
                                    upstream.allowDiscard();
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
                        if (!responseDone) {
                            writeData(Unpooled.EMPTY_BUFFER, true, ctx.voidPromise());
                            if (finish()) {
                                flush();
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
            if (!ctx.executor().inEventLoop()) {
                ctx.executor().execute(() -> writeStreaming(response, upstream, contentLength));
                return;
            }

            if (responseDone) {
                // connection closed?
                writerUpstream.allowDiscard();
                writerUpstream.disregardBackpressure();
                return;
            }

            writerUpstream = upstream;

            prepareCompression(response, contentLength);

            writeHeaders(response, false, ctx.voidPromise());
            upstream.start();
        }

        @Override
        public void writeHeadResponse(@NonNull HttpResponse response) {
            response.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
            writeFull(response, Unpooled.EMPTY_BUFFER);
        }

        private void writeFull(@NonNull HttpResponse response, @NonNull ByteBuf content) {
            if (responseDone) {
                // early check
                throw new IllegalStateException("Response already written");
            }
            if (!ctx.executor().inEventLoop()) {
                ByteBuf finalContent = content;
                ctx.executor().execute(() -> writeFull(response, finalContent));
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

            writeHeaders(response, empty, ctx.voidPromise());
            if (!empty) {
                // bypass writeDataCompressing
                writeData0(content, true, ctx.voidPromise());
            } else if (content != null) {
                content.release();
            }
            if (!finish()) {
                throw new IllegalStateException("Response already written");
            }
            flush();
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
                Compressor.Session session = compressor.prepare(ctx, request, headers, contentLength);
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
                writeDataCompressing(data, endStream, promise);
            }
        }

        private void writeDataCompressing(ByteBuf data, boolean endStream, ChannelPromise promise) {
            Compressor.Session compressionChannel = this.compressionSession;
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
        private class InputStreamer implements BufferConsumer.Upstream, ByteBufConsumer {
            final StreamingNettyByteBody.SharedBuffer dest = new StreamingNettyByteBody.SharedBuffer(ctx.channel().eventLoop(), bodySizeLimits, this);
            /**
             * Number of bytes that have been received by {@link #add(ByteBuf)} but the downstream
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
                EventLoop eventLoop = ctx.channel().eventLoop();
                if (!eventLoop.inEventLoop()) {
                    eventLoop.execute(this::start);
                    return;
                }

                if (sendContinue) {
                    writeHeaders(PipeliningServerHandler.ContinueOutboundHandler.CONTINUE_11, false, ctx.voidPromise());
                    sendContinue = false;
                }
            }

            @Override
            public void onBytesConsumed(long bytesConsumed) {
                if (bytesConsumed < 0) {
                    throw new IllegalArgumentException("Negative bytes consumed");
                }

                EventLoop eventLoop = ctx.channel().eventLoop();
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
                EventLoop eventLoop = ctx.channel().eventLoop();
                if (!eventLoop.inEventLoop()) {
                    eventLoop.execute(this::allowDiscard);
                    return;
                }

                closeInput();
                dest.discard(); // signal discard
            }

            @Override
            public void disregardBackpressure() {
                EventLoop eventLoop = ctx.channel().eventLoop();
                if (!eventLoop.inEventLoop()) {
                    eventLoop.execute(this::disregardBackpressure);
                    return;
                }

                unacknowledged = Long.MIN_VALUE;
            }

            @Override
            public void add(ByteBuf buf) {
                assert ctx.channel().eventLoop().inEventLoop();

                if (unacknowledged < 0) {
                    // -MIN_VALUE is still MIN_VALUE so we need to special case it
                    notifyDataConsumedLong(unacknowledged == Long.MIN_VALUE ? buf.readableBytes() : Math.min(buf.readableBytes(), -unacknowledged));
                }
                unacknowledged += buf.readableBytes();
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
