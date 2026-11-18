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
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
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
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.util.concurrent.EventExecutor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;
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

    /**
     * The connection, set when the handler is added to the pipeline. The streams take it as a
     * final field instead of reading this one.
     */
    @Nullable
    ConnectionContext connection;
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

    final ConnectionContext requiredConnection() {
        return Objects.requireNonNull(connection, "connection");
    }

    protected ChannelHandlerContext requiredCtx() {
        return requiredConnection().ctx();
    }

    /**
     * The request side of a stream. The states that hold something are the holders themselves:
     * the request data buffered for the next read complete ({@link Buffering}), and the
     * streaming request body ({@link MultiplexedStream.InputStreamer}), which stays the state
     * after the body ended or failed.
     */
    private sealed interface InputState {
    }

    private enum InputPhase implements InputState {
        /**
         * No request headers were read yet.
         */
        AWAITING_HEADERS,
        /**
         * The request headers were read, the request is not accepted yet and no data is buffered
         * for it.
         */
        HEADERS_RECEIVED,
        /**
         * The request was accepted with its full body.
         */
        ACCEPTED
    }

    /**
     * The request data that is buffered until the end of the request or the next read complete,
     * whichever comes first. The list is the state, so that buffering allocates nothing more than
     * the list.
     */
    @SuppressWarnings("serial") // never serialized
    private static final class Buffering extends ArrayList<ByteBuf> implements InputState {
        /**
         * Number of bytes in the list.
         */
        private long length;
        /**
         * Whether the request was accepted without this data, by request headers that ended the
         * stream after the data was buffered. The data is then only released when the stream is
         * closed.
         */
        private boolean accepted;
    }

    /**
     * The response side of a stream.
     */
    private enum OutputState {
        NOT_STARTED,
        /**
         * The headers of the response were handed to the connection, even if writing them
         * failed: no other headers, including an interim 100 Continue, may follow them.
         */
        HEADERS_WRITTEN,
        /**
         * The response was written or given up, and the request handler was told so.
         */
        FINISHED
    }

    /**
     * How a stream ended. A reset stream is one that either side reset or that closed before
     * its response was written, a closed stream is one that the transport closed: no more of it
     * is read. A stream can be reset before it is closed, and closed without being reset when
     * its response was written.
     */
    private enum StreamEnd {
        OPEN, RESET, CLOSED, RESET_AND_CLOSED;

        boolean isReset() {
            return this == RESET || this == RESET_AND_CLOSED;
        }

        boolean isClosed() {
            return this == CLOSED || this == RESET_AND_CLOSED;
        }

        StreamEnd reset() {
            return switch (this) {
                case OPEN -> RESET;
                case CLOSED -> RESET_AND_CLOSED;
                case RESET, RESET_AND_CLOSED -> this;
            };
        }

        StreamEnd close() {
            return switch (this) {
                case OPEN -> CLOSED;
                case RESET -> RESET_AND_CLOSED;
                case CLOSED, RESET_AND_CLOSED -> this;
            };
        }
    }

    /**
     * An HTTP/2 or HTTP/3 stream. Its lifecycle is held in three fields that are confined to the
     * event loop: {@link #input} for the request, {@link #output} for the response and
     * {@link #end} for the stream itself.
     */
    abstract class MultiplexedStream implements OutboundAccess {
        /**
         * The connection of the stream.
         */
        final ConnectionContext connection;
        private boolean skipCompression;
        @Nullable
        private final Http2RequestEvent jfrEvent;
        @Nullable
        private HttpRequest request;
        private InputState input = InputPhase.AWAITING_HEADERS;
        private OutputState output = OutputState.NOT_STARTED;
        private StreamEnd end = StreamEnd.OPEN;
        /**
         * The writer of the streaming response with its upstream, once it is up for writing.
         */
        @Nullable
        private ResponseStreamer responseStreamer;

        @Nullable
        private Object attachment;

        /**
         * {@code true} iff a task to resume the response writer is submitted and has not run yet,
         * see {@link #onOutboundWritable()}.
         */
        private boolean outboundWritableScheduled;
        private Compressor. @Nullable Session compressionSession;
        /**
         * The callbacks of {@link #whenAbandoned(Runnable)}, guarded by this stream, or
         * {@code null} if none is registered.
         */
        @Nullable
        private List<Runnable> abandonCallbacks;
        /**
         * Whether the stream was abandoned, guarded by this stream.
         */
        private boolean abandoned;
        /**
         * Set once the promise of the final write of the response is created, before the write
         * is submitted.
         */
        private volatile boolean finalWriteIssued;
        private volatile boolean recordFinalWrite;
        /**
         * The recorded promise of the final write, see {@link #recordFinalWrite()}. Confined to
         * the event loop.
         */
        private @Nullable ChannelPromise finalWrite;

        MultiplexedStream(ConnectionContext connection, int streamId) {
            this.connection = connection;
            if (JfrSupport.isRecorderInitialized() && Http2RequestEvent.isTurnedOn()) {
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
         * Whether data written to this stream now still fits the peer's flow control window and
         * the channel is writable, so that a streamed response may report it as consumed right
         * away instead of waiting for the frames to be written. The data queued by the protocol
         * stays bounded by the window. Protocols without such a signal return {@code false}.
         *
         * @return {@code true} iff more data can be queued without exceeding the window
         */
        boolean isOutboundWritable() {
            return false;
        }

        /**
         * Called by the protocol when {@link #isOutboundWritable()} became {@code true}. The
         * protocol may call this while it writes queued data (e.g. from the HTTP/2 flow controller
         * listener), so the writer resumes in a separate task: data written synchronously here
         * would be queued while the protocol is still writing.
         */
        final void onOutboundWritable() {
            if (outboundWritableScheduled) {
                return;
            }
            outboundWritableScheduled = true;
            connection.ctx().executor().execute(() -> {
                outboundWritableScheduled = false;
                ResponseStreamer current = responseStreamer;
                if (current != null && output != OutputState.FINISHED && !end.isReset() && isOutboundWritable()) {
                    current.writer.onWritable();
                }
            });
        }

        /**
         * Called when the request headers are read.
         *
         * @param headers The headers
         * @param endOfStream Whether this is the last request packet
         */
        final void onHeadersRead(HttpRequest headers, boolean endOfStream) {
            InputState current = input;
            if (isAccepted(current)) {
                throw new IllegalStateException("Request already accepted");
            }

            // headers that are read again before the request is accepted replace it
            this.request = headers;
            if (endOfStream) {
                if (current instanceof Buffering buffering) {
                    buffering.accepted = true;
                } else {
                    input = InputPhase.ACCEPTED;
                }
                accept(headers, NettyByteBodyFactory.empty());
            } else if (current == InputPhase.AWAITING_HEADERS) {
                input = InputPhase.HEADERS_RECEIVED;
            }
        }

        /**
         * @param state A state of the request side
         * @return Whether the request was handed to the request handler in that state
         */
        private boolean isAccepted(InputState state) {
            return state == InputPhase.ACCEPTED || state instanceof InputStreamer || state instanceof Buffering buffering && buffering.accepted;
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
            InputState current = input;
            if (current instanceof InputStreamer streamer) {
                streamer.add(byteBodyFactory().readBufferFactory().adapt(data));
                if (endOfStream) {
                    streamer.terminated = true;
                    streamer.complete();
                }
            } else if (end.isClosed()) {
                // no request will be accepted for this stream anymore
                data.release();
            } else if (isAccepted(current)) {
                throw new IllegalStateException("Request already accepted");
            } else if (current == InputPhase.AWAITING_HEADERS) {
                data.release();
                throw new IllegalStateException("Request headers not received");
            } else if (endOfStream) {
                // we got the full message before readComplete
                acceptFullBody(data, null);
            } else {
                Buffering buffering = current instanceof Buffering b ? b : null;
                if ((buffering == null ? 0 : buffering.length) + data.readableBytes() > bodySizeLimits.maxBufferSize()) {
                    // a decompressed body can grow far beyond the flow control window, so stop
                    // buffering before the limit is exceeded and let the streaming buffer apply
                    // the size limits and backpressure
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
            return 0;
        }

        /**
         * Accept the request with the buffered content and the given last piece as its full body.
         *
         * @param data     The last piece of the body. Release ownership is transferred to this method
         * @param trailers The trailers of the request, or {@code null} if there are none
         */
        private void acceptFullBody(ByteBuf data, @Nullable HttpHeaders trailers) {
            ByteBuf fullBody;
            if (input instanceof Buffering pieces) {
                if (data.isReadable()) {
                    pieces.add(data);
                } else {
                    data.release();
                }
                // composeBody takes ownership of the pieces even when it fails
                input = InputPhase.HEADERS_RECEIVED;
                fullBody = pieces.size() == 1 ? pieces.get(0) : PipeliningServerHandler.composeBody(connection.alloc(), pieces);
            } else {
                fullBody = data;
            }

            input = InputPhase.ACCEPTED;
            notifyDataConsumed(fullBody.readableBytes());
            NettyByteBodyFactory byteBodyFactory = byteBodyFactory();
            CloseableByteBody body = byteBodyFactory.createChecked(bodySizeLimits, fullBody);
            if (trailers != null) {
                body = byteBodyFactory.withTrailers(body, trailers);
            }
            accept(Objects.requireNonNull(request), body);
        }

        /**
         * Called when the trailers of the request are read: the headers frame that ends the
         * stream after the request headers. The END_STREAM flag they carry completes the request
         * body.
         *
         * @param trailers The trailers
         * @param endOfStream Whether this is the last request packet. Trailers that do not end
         *                    the stream are a protocol violation and are ignored
         */
        final void onTrailersRead(HttpHeaders trailers, boolean endOfStream) {
            if (!endOfStream || end != StreamEnd.OPEN) {
                return;
            }
            InputState current = input;
            if (current instanceof InputStreamer streamer) {
                if (!streamer.terminated) {
                    streamer.terminated = true;
                    streamer.completeWithTrailers(trailers);
                }
            } else if (current != InputPhase.AWAITING_HEADERS && !isAccepted(current)) {
                // the full request arrived before read complete
                acceptFullBody(Unpooled.EMPTY_BUFFER, trailers);
            }
            // else there are no headers yet, or the request had no body and was accepted with
            // the headers
        }

        /**
         * Hand the request to the request handler. A failure of the handler is answered on this
         * stream only, as on HTTP/1: thrown into the frame decoder, it would fail the whole
         * connection with every other stream on it, and the body would never be released.
         *
         * @param request The request
         * @param body    The body of the request, which the handler owns once accepted
         */
        private void accept(HttpRequest request, CloseableByteBody body) {
            try {
                requestHandler.accept(connection.ctx(), request, body, this);
            } catch (Exception e) {
                try {
                    // closes nothing once the handler claimed the body
                    body.close();
                } catch (RuntimeException closeError) {
                    e.addSuppressed(closeError);
                }
                try {
                    requestHandler.handleUnboundError(e);
                } catch (RuntimeException handlerError) {
                    LOG.debug("Failed to handle an error from the request handler", handlerError);
                }
                rejectUnaccepted();
            }
        }

        /**
         * Answer a request that the request handler failed to accept with a 500. If the handler
         * started a response before it failed, that response may be cut: the stream is reset.
         */
        private void rejectUnaccepted() {
            if (output != OutputState.NOT_STARTED || responseStreamer != null) {
                abort();
                return;
            }
            try {
                write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR), NettyByteBodyFactory.empty());
            } catch (RuntimeException e) {
                LOG.debug("Failed to write the error response for a request that was not accepted", e);
                abort();
            }
        }

        /**
         * Called on read complete. This makes the stream devolve into streaming mode, i.e. give up
         * on buffering data in hopes of reading it all in one go.
         */
        final void devolveToStreaming() {
            InputState previous = input;
            if (end.isClosed() || previous == InputPhase.AWAITING_HEADERS || isAccepted(previous)) {
                return;
            }
            HttpRequest headers = Objects.requireNonNull(request);
            InputStreamer streamer = new InputStreamer(HttpUtil.is100ContinueExpected(headers));
            input = streamer;
            // set the expected length before replaying the buffered frames, like the HTTP/1 path:
            // the declared Content-Length is charged to the size limit in full, and frames that
            // arrive without a known length are charged individually, so the buffered frames
            // would be counted twice if they were added first
            streamer.dest.setExpectedLengthFrom(headers.headers());
            if (previous instanceof Buffering pieces) {
                for (ByteBuf buf : pieces) {
                    streamer.add(byteBodyFactory().readBufferFactory().adapt(buf));
                }
            }
            accept(headers, new StreamingNettyByteBody(streamer.dest));
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
            end = end.reset();
            abandon();
            if (input instanceof InputStreamer streamer) {
                streamer.terminated = true;
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
            end = end.close();
            if (input instanceof InputStreamer streamer && !streamer.terminated) {
                // the body is streaming and will not receive any more data: fail it, so that its
                // consumer stops waiting and releases what it holds. A reset failed the body that
                // was streaming then, but not one that the request was accepted with afterwards
                onRstStreamRead(new ClosedChannelException());
            }
            if (output != OutputState.FINISHED) {
                if (responseStreamer != null) {
                    // a streaming response is in progress, and it cannot be completed any more.
                    // The body may never signal again (e.g. an idle event stream), so finish
                    // here: that discards the body and cleans up the request. The response did
                    // not complete, so the request is abandoned first: finish() marks the stream
                    // finished, which onStreamClosed would take for a completed response
                    end = end.reset();
                    abandon();
                    finish();
                } else {
                    // the response will never be written: let its body release what it holds.
                    // A response that is written later finishes on the closed stream.
                    disposeWriteSide();
                }
            }
            if (input instanceof Buffering pieces) {
                input = pieces.accepted ? InputPhase.ACCEPTED : InputPhase.HEADERS_RECEIVED;
                for (ByteBuf buf : pieces) {
                    buf.release();
                }
            }
        }

        /**
         * Called when the stream is closed, by either side or with the connection: if the
         * response was not written, the request is abandoned, and the stream cannot take the
         * response any more, like a stream the client reset: e.g. a stream that Netty reset for a
         * stream error, while the request waited for its route.
         */
        final void onStreamClosed() {
            if (output != OutputState.FINISHED || finalWritePending()) {
                end = end.reset();
                abandon();
            }
        }

        /**
         * Run the callbacks of {@link #whenAbandoned(Runnable)}, once, on the event loop, after
         * the event that abandoned the stream: a callback may answer the request, which the
         * closed stream drops.
         */
        private void abandon() {
            List<Runnable> callbacks;
            synchronized (this) {
                if (abandoned) {
                    return;
                }
                abandoned = true;
                callbacks = abandonCallbacks;
                abandonCallbacks = null;
            }
            if (callbacks != null) {
                connection.ctx().executor().execute(() -> {
                    for (Runnable callback : callbacks) {
                        callback.run();
                    }
                });
            }
        }

        @Override
        public final Runnable whenAbandoned(Runnable callback) {
            // the request of a stream is abandoned with the stream, not with the connection
            synchronized (this) {
                if (!abandoned) {
                    List<Runnable> callbacks = abandonCallbacks;
                    if (callbacks == null) {
                        callbacks = new ArrayList<>(1);
                        abandonCallbacks = callbacks;
                    }
                    callbacks.add(callback);
                    return () -> removeAbandonCallback(callback);
                }
            }
            // already abandoned: at once on the event loop, or on it
            EventExecutor executor = connection.ctx().executor();
            if (executor.inEventLoop()) {
                callback.run();
            } else {
                executor.execute(callback);
            }
            return () -> { };
        }

        @Override
        public final synchronized boolean isAbandoned() {
            return abandoned;
        }

        private synchronized void removeAbandonCallback(Runnable callback) {
            List<Runnable> callbacks = abandonCallbacks;
            if (callbacks != null) {
                callbacks.remove(callback);
                if (callbacks.isEmpty()) {
                    abandonCallbacks = null;
                }
            }
        }

        private void disposeWriteSide() {
            ResponseStreamer streamer = responseStreamer;
            if (streamer != null) {
                streamer.discard();
            }
            if (compressionSession != null) {
                compressionSession.discard();
            }
        }

        private boolean finish() {
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

            if (!connection.ctx().executor().inEventLoop() && body instanceof AvailableByteBody available) {
                // e.g. a blocking route. The bytes are read here, so that a failure to read them
                // reaches the caller, which answers with an error response instead
                prepareHeaders(response, body);
                ByteBuf content = NettyByteBodyFactory.toByteBuf(available);
                try {
                    connection.ctx().executor().execute(() -> writeFullClaimed(response, content));
                } catch (Throwable t) {
                    // the event loop shut down
                    content.release();
                    throw t;
                }
                return;
            }
            if (!connection.ctx().executor().inEventLoop()) {
                // e.g. the first event of a blocking server-sent events handler. The body is
                // claimed here and the response is written on the event loop, like every write
                // of the stream: the event loop may already feed the body, and the response
                // writer takes its signals from one thread at a time
                CloseableByteBody claimed = body.move();
                try {
                    connection.ctx().executor().execute(() -> writeClaimed(response, claimed));
                } catch (Throwable t) {
                    // the event loop shut down: the caller no longer closes the body
                    claimed.close();
                    throw t;
                }
                return;
            }

            prepareHeaders(response, body);

            NettyByteBodyFactory byteBodyFactory = byteBodyFactory();
            if (body instanceof AvailableByteBody available) {
                writeFull(response, NettyByteBodyFactory.toByteBuf(available));
            } else {
                StreamingNettyByteBody snbb = byteBodyFactory.toStreaming(body);
                ResponseStreamer consumer = new ResponseStreamer(response, snbb, snbb.expectedLength().orElse(-1));
                // a body with data already available (e.g. a relayed client response) delivers it
                // from primary(). The writer holds it back until the response is opened, which
                // writes the HEADERS of the stream first.
                BufferConsumer.Upstream upstream = snbb.primary(consumer.writer);
                consumer.writer.attach(upstream);
                consumer.writer.execute(() -> consumer.open(upstream));
            }
        }

        private NettyByteBodyFactory byteBodyFactory() {
            return new NettyByteBodyFactory(connection.channel());
        }

        private static void prepareHeaders(HttpResponse response, ByteBody body) {
            response.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
            if (PipeliningServerHandler.canHaveBody(response.status())) {
                OptionalLong length = body.expectedLength();
                if (length.isPresent()) {
                    ContentLengthValues.set(response.headers(), length.getAsLong());
                } else {
                    // the length is unknown, a header set by the application may not match the data
                    response.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
                }
            } else {
                response.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
            }
        }

        /**
         * Write a buffered response whose bytes were read on another thread.
         *
         * @param response The response
         * @param content  The bytes of the body
         */
        private void writeFullClaimed(HttpResponse response, ByteBuf content) {
            try {
                writeFull(response, content);
            } catch (Throwable t) {
                giveUpOffLoopWrite(t);
            }
        }

        /**
         * Write a response whose body was claimed on another thread.
         *
         * @param response The response
         * @param body     The claimed body
         */
        private void writeClaimed(HttpResponse response, CloseableByteBody body) {
            if (output == OutputState.FINISHED) {
                // the caller no longer closes the body
                body.close();
                return;
            }
            try {
                write(response, body);
            } catch (Throwable t) {
                body.close();
                giveUpOffLoopWrite(t);
            }
        }

        /**
         * Give up on a response whose write moved to the event loop and failed there: the
         * caller is gone, so there is no one to answer a fallback response.
         *
         * @param t The failure
         */
        private void giveUpOffLoopWrite(Throwable t) {
            LOG.warn("Failed to write the response: {}", t.getMessage(), t);
            requestHandler.responseFailed(attachment, t);
            abortWrittenResponse();
        }

        /**
         * End the stream of a streamed response: with the trailers of the body when it carries
         * any, else with a data frame. The body completes its trailers before it completes
         * the writer, so they are available here.
         *
         * @param last     The final bytes of the body, possibly empty
         * @param body     The body
         * @param response The response
         */
        private void writeEnd(ByteBuf last, StreamingNettyByteBody body, HttpResponse response) {
            HttpHeaders trailers = NettyByteBodyFactory.trailersToSend(body);
            if (trailers == null) {
                writeData(last, true, endPromise(response));
                return;
            }
            if (last.isReadable()) {
                // followed by the trailers, which carry the promise
                writeData(last, false, silentVoidPromise());
            } else {
                last.release();
            }
            if (compressionSession != null) {
                // the trailers are not compressed: flush the compressed data first
                compressionSession.finish();
                ByteBuf compressed = compressionSession.poll();
                if (compressed != null) {
                    writeData0(compressed, false, connection.ctx().voidPromise());
                }
            }
            writeTrailers(trailers, endPromise(response));
        }

        @Override
        public void writeHeadResponse(HttpResponse response) {
            if (!connection.ctx().executor().inEventLoop()) {
                connection.ctx().executor().execute(() -> {
                    try {
                        writeHeadResponse(response);
                    } catch (Throwable t) {
                        giveUpOffLoopWrite(t);
                    }
                });
                return;
            }
            response.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
            writeFull(response, Unpooled.EMPTY_BUFFER);
        }

        private void writeFull(HttpResponse response, ByteBuf content) {
            assert connection.ctx().executor().inEventLoop();
            if (output == OutputState.FINISHED) {
                content.release();
                return;
            } else if (end != StreamEnd.OPEN) {
                // stream reset by either side, or connection closed. Writing to a stream that
                // netty has already closed would fail the whole connection.
                content.release();
                finish();
                return;
            }
            if (output == OutputState.HEADERS_WRITTEN) {
                // the write of the response failed after its headers: a second HEADERS frame
                // would be read as trailers
                content.release();
                abortWrittenResponse();
                return;
            }

            try {
                boolean empty;
                // the bytes to send, null if the compression produced none
                @Nullable ByteBuf data;
                try (OwnedBuffer owned = new OwnedBuffer(content)) {
                    empty = !content.isReadable();
                    if (!empty) {
                        prepareCompression(response, content.readableBytes());
                    }
                    Compressor.Session session = compressionSession;
                    if (session == null) {
                        data = owned.take();
                    } else {
                        session.push(owned.take());
                        session.finish();
                        session.fixContentLength(response);
                        data = session.poll();
                        empty = data == null;
                    }
                }
                try (OwnedBuffer owned = new OwnedBuffer(data == null ? Unpooled.EMPTY_BUFFER : data)) {
                    headersHandedOver();
                    writeHeaders(response, empty, empty ? endPromise(response) : connection.ctx().voidPromise());
                    if (!empty) {
                        ChannelPromise end = endPromise(response);
                        // bypass writeDataCompressing
                        writeData0(owned.take(), true, end);
                    }
                }
            } catch (Throwable t) {
                Compressor.Session failed = compressionSession;
                if (output == OutputState.NOT_STARTED && failed != null) {
                    // the caller may still send a fallback response, which must not reuse the failed session
                    compressionSession = null;
                    failed.discard();
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
            if (!connection.ctx().executor().inEventLoop()) {
                connection.ctx().executor().execute(this::abort);
                return;
            }
            if (output != OutputState.FINISHED) {
                abortWrittenResponse();
            }
        }

        /**
         * Reset the stream of a response that cannot be completed, and finish it.
         */
        private void abortWrittenResponse() {
            if (!end.isReset()) {
                reset(new IllegalStateException("The response could not be written"));
            }
            if (finish()) {
                flush();
            }
        }

        /**
         * The headers of the response are handed to the connection now.
         */
        private void headersHandedOver() {
            if (output == OutputState.NOT_STARTED) {
                output = OutputState.HEADERS_WRITTEN;
            }
        }

        /**
         * @return Whether nothing can be written to the stream any more: the response is
         * finished, or the stream is reset or closed
         */
        private boolean isGone() {
            return output == OutputState.FINISHED || end != StreamEnd.OPEN;
        }

        @Override
        public final boolean recordFinalWrite() {
            // a volatile write before the read, the reverse of endPromise: one of them sees the other
            recordFinalWrite = true;
            return !finalWriteIssued;
        }

        @Override
        public final @Nullable ChannelFuture finalWrite() {
            return finalWrite;
        }

        /**
         * @return Whether the recorded final write has not completed successfully yet
         */
        private boolean finalWritePending() {
            ChannelPromise promise = finalWrite;
            return promise != null && !promise.isSuccess();
        }

        private ChannelPromise endPromise(HttpResponse response) {
            finalWriteIssued = true;
            boolean record = recordFinalWrite;
            if (jfrEvent == null) {
                if (record) {
                    ChannelPromise promise = connection.ctx().newPromise();
                    finalWrite = promise;
                    return promise;
                }
                return connection.ctx().voidPromise();
            }
            ChannelPromise promise = connection.ctx().newPromise();
            if (record) {
                finalWrite = promise;
            }
            return promise.addListener((ChannelFutureListener) future -> {
                jfrEvent.end();
                if (jfrEvent.shouldCommit()) {
                    jfrEvent.populateChannel(connection.channel());
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

        @Nullable
        final Object attachment() {
            return attachment;
        }

        @Override
        public final void closeAfterWrite() {
        }

        @Override
        public final void skipCompression() {
            skipCompression = true;
        }

        private void prepareCompression(HttpResponse headers, long contentLength) {
            if (compressor != null && !skipCompression) {
                Compressor.Session session = compressor.prepare(connection.ctx(), Objects.requireNonNull(request), headers, contentLength);
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
         * Write the response trailers, ending the stream.
         *
         * @param trailers The trailers
         * @param promise The promise to complete when the trailers are written
         * @since 5.3.0
         */
        abstract void writeTrailers(HttpHeaders trailers, ChannelPromise promise);

        /**
         * This is the {@link HotObservable} that represents the request body in the streaming
         * request case.
         */
        private final class InputStreamer implements BufferConsumer.Upstream, BufferConsumer, InputState {
            final StreamingNettyByteBody.SharedBuffer dest = byteBodyFactory().createStreamingBuffer(bodySizeLimits, this);
            /**
             * Whether the end of the request body or its failure was handed to {@link #dest}.
             */
            boolean terminated;
            /**
             * Number of bytes that have been received by {@link #add(ReadBuffer)} but the downstream
             * hasn't consumed ({@link #onBytesConsumed(long)}). May be negative if the downstream
             * has signaled more consumption.
             */
            long unacknowledged = 0;
            boolean sendContinue;

            InputStreamer(boolean sendContinue) {
                this.sendContinue = sendContinue;
                // what arrives before the route reads the body is bounded by the flow control
                // window, not by the buffer limit, for a reader that streams the body without
                // holding it
                dest.setKeepInitialBytes();
            }

            @Override
            public void start() {
                EventLoop eventLoop = connection.eventLoop();
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
                writeHeaders(PipeliningServerHandler.ContinueOutboundHandler.CONTINUE_11, false, connection.ctx().voidPromise());
                flush();
            }

            @Override
            public void onBytesConsumed(long bytesConsumed) {
                if (bytesConsumed < 0) {
                    throw new IllegalArgumentException("Negative bytes consumed");
                }

                EventLoop eventLoop = connection.eventLoop();
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
                EventLoop eventLoop = connection.eventLoop();
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
                EventLoop eventLoop = connection.eventLoop();
                if (!eventLoop.inEventLoop()) {
                    eventLoop.execute(this::disregardBackpressure);
                    return;
                }

                unacknowledged = Long.MIN_VALUE;
            }

            @Override
            public void add(ReadBuffer buf) {
                assert connection.eventLoop().inEventLoop();

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

            void completeWithTrailers(HttpHeaders trailers) {
                dest.completeWithTrailers(trailers);
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
            final StreamingNettyByteBody body;
            final long contentLength;
            final StreamingResponseWriter writer = new StreamingResponseWriter(connection.eventLoop(), this, connection.alloc());
            /**
             * The upstream of the body, set when this streamer becomes the
             * {@link #responseStreamer} of the stream.
             */
            private BufferConsumer.@Nullable Upstream upstream;
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

            ResponseStreamer(HttpResponse response, StreamingNettyByteBody body, long contentLength) {
                this.response = response;
                this.body = body;
                this.contentLength = contentLength;
            }

            /**
             * Called on the event loop, in order with the body signals that arrived before, once
             * the body is subscribed to. The response is opened now: the writer holds back any
             * pieces that arrived before this, so the HEADERS of the stream go out first.
             */
            void open(BufferConsumer.Upstream upstream) {
                if (isGone() || output != OutputState.NOT_STARTED) {
                    // the stream is gone (e.g. the connection closed) before the response could
                    // start. Disposing the writer reports responseWritten, which finishes the
                    // stream if that has not happened yet
                    upstream.allowDiscard();
                    upstream.disregardBackpressure();
                    if (output == OutputState.HEADERS_WRITTEN && end == StreamEnd.OPEN) {
                        // a response whose write failed after its headers: a second HEADERS
                        // frame would be read as trailers. A stream that is reset or closed is
                        // not reset again: writing to a stream that netty has already closed
                        // would fail the whole connection
                        abortWrittenResponse();
                    }
                    writer.dispose();
                    return;
                }

                this.upstream = upstream;
                responseStreamer = this;
                writer.open();
            }

            @Override
            public void open() {
                prepareCompression(response, contentLength);
                headersHandedOver();
                writeHeaders(response, false, connection.ctx().voidPromise());
            }

            @Override
            public void write(ByteBuf data, boolean last) {
                if (isGone()) {
                    // the stream is gone: the upstream is told to discard when the stream is
                    // finished. The end of stream frame may use a void promise, so it must not be
                    // written to a stream that netty has already closed
                    data.release();
                    return;
                }
                ByteBuf previous = held;
                held = null;
                if (!last) {
                    held = data;
                    // followed by the held piece in the same batch, which carries the promise
                    if (previous != null && !writeMiddle(previous, silentVoidPromise())) {
                        flush();
                        return;
                    }
                    if (!batchPending) {
                        batchPending = true;
                        pendingBatches.add(this);
                    }
                } else {
                    // the final bytes go out with the frame that ends the stream
                    if (previous != null) {
                        if (data.isReadable()) {
                            if (!writeMiddle(previous, silentVoidPromise())) {
                                data.release();
                                flush();
                                return;
                            }
                        } else {
                            data.release();
                            data = previous;
                        }
                    }
                    try {
                        writeEnd(data, body, response);
                    } catch (Throwable t) {
                        // e.g. trailers that cannot be converted: the writer still reports the
                        // response as written, which cleans up the request
                        LOG.warn("Failed to end the streamed response: {}", t.getMessage(), t);
                        requestHandler.responseFailed(attachment, t);
                        reset(t);
                    }
                }
                flush();
            }

            @Override
            public boolean isWritable() {
                // while the window has room, written bytes count as consumed right away;
                // otherwise consumption is reported once the batch is written, see endBatch, or
                // when the stream becomes writable again, see onOutboundWritable
                return !isGone() && isOutboundWritable();
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
                if (isGone()) {
                    data.release();
                    return;
                }
                writeMiddle(data, connection.ctx().newPromise()
                    .addListener((ChannelFutureListener) future -> batchWritten(future, n)));
            }

            /**
             * Write a piece before the end of the body. The first piece may create the compression encoder, which
             * can fail after the headers went out: the stream is then reset.
             *
             * @return {@code true} iff the piece was written
             */
            @SuppressWarnings("java:S1181") // a compression provider may fail with an Error, e.g. a native one that cannot link; the stream must still reset
            private boolean writeMiddle(ByteBuf data, ChannelPromise promise) {
                try {
                    writeData(data, false, promise);
                    return true;
                } catch (Throwable t) {
                    LOG.warn("Failed to write the streamed response: {}", t.getMessage(), t);
                    requestHandler.responseFailed(attachment, t);
                    reset(t);
                    // the body may still be open: disposing the writer reports the response as
                    // written, which lets the upstream discard the rest and releases what is held
                    writer.dispose();
                    return false;
                }
            }

            private void batchWritten(ChannelFuture future, long n) {
                if (future.isSuccess()) {
                    writer.bytesConsumed(n);
                } else {
                    logStreamWriteFailure(future.cause());
                    writer.allowDiscard();
                }
            }

            /**
             * Let the body discard what it did not deliver yet, and release the piece that is
             * held back.
             */
            void discard() {
                BufferConsumer.Upstream bodyUpstream = upstream;
                if (bodyUpstream != null) {
                    bodyUpstream.allowDiscard();
                    bodyUpstream.disregardBackpressure();
                }
                ByteBuf data = held;
                if (data != null) {
                    held = null;
                    data.release();
                }
            }

            @Override
            public void fail(Throwable e) {
                requestHandler.responseFailed(attachment, e);
                if (!reset(e)) {
                    LOG.warn("The streamed response body failed after some of it was written. The error cannot be forwarded to the client, and the response ends abruptly.", e);
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
