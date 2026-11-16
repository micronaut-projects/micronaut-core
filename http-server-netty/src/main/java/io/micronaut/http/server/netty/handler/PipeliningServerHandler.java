/*
 * Copyright 2017-2023 original authors
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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.netty.EventLoopFlow;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.netty.body.StreamingNettyByteBody;
import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.micronaut.http.server.netty.HttpCompressionStrategy;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.micronaut.runtime.graceful.GracefulShutdownCapable;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.compression.BrotliDecoder;
import io.netty.handler.codec.compression.DecompressionException;
import io.netty.handler.codec.compression.SnappyFrameDecoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.io.EOFException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.CompletionStage;

/**
 * Netty handler that handles incoming {@link HttpRequest}s and forwards them to a
 * {@link RequestHandler}.
 *
 * @author Jonas Konrad
 * @since 4.0.0
 */
@Internal
public final class PipeliningServerHandler extends ChannelInboundHandlerAdapter implements GracefulShutdownCapable {
    /**
     * Maximum number of components of the composite a buffered request body is assembled from.
     * The allocator default of 16 would consolidate (copy) the body as soon as it arrived in more
     * than 16 pieces; see {@link #composeBody}.
     */
    static final int MAX_COMPOSITE_COMPONENTS = 4096;

    private static final Logger LOG = LoggerFactory.getLogger(PipeliningServerHandler.class);
    private static final String DECOMPRESSOR_HANDLER = "decompressor";

    private final RequestHandler requestHandler;
    private final boolean quic;

    // these three handlers can be reused and are cached here
    private final DroppingInboundHandler droppingInboundHandler = new DroppingInboundHandler();
    private final InboundHandler baseInboundHandler = new MessageInboundHandler();
    private final OptimisticBufferingInboundHandler optimisticBufferingInboundHandler = new OptimisticBufferingInboundHandler();

    @Nullable
    private Compressor compressor;
    private BodySizeLimits bodySizeLimits = BodySizeLimits.UNLIMITED;
    private boolean requestDecompressionEnabled = true;

    private final InboundContext inbound = new InboundContext();
    /**
     * Runs once the application discards the body of the current request it streamed.
     */
    @Nullable
    private Runnable requestBodyDiscardListener;

    /**
     * Queue of outbound messages that can't be written yet.
     */
    private final Deque<OutboundAccessImpl> outboundQueue = new ArrayDeque<>(1);
    /**
     * Current outbound message, or {@code null} if no outbound message is waiting.
     */
    @Nullable
    private OutboundHandler outboundHandler = null;

    @Nullable
    private ChannelHandlerContext ctx;
    /**
     * {@code true} iff we are in a read operation, before {@link #channelReadComplete}.
     */
    private boolean reading = false;
    /**
     * {@code true} iff {@code ctx.read()} has been called already.
     */
    private boolean readCalled = false;
    /**
     * {@code true} iff this handler has been removed.
     */
    private boolean removed = false;
    /**
     * {@code true} iff we should flush on {@link #channelReadComplete}.
     */
    private boolean flushPending = false;
    /**
     * {@code true} inside {@link #writeSome()} to avoid reentrancy.
     */
    private boolean writing = false;
    private boolean quicWritePending = false;
    private boolean shuttingDown = false;

    public PipeliningServerHandler(RequestHandler requestHandler) {
        this(requestHandler, false);
    }

    public PipeliningServerHandler(RequestHandler requestHandler, boolean quic) {
        this.requestHandler = requestHandler;
        this.quic = quic;
    }

    private ChannelHandlerContext requiredCtx() {
        return Objects.requireNonNull(ctx, "ctx");
    }

    public void setCompressionStrategy(HttpCompressionStrategy compressionStrategy) {
        if (compressionStrategy.isEnabled()) {
            this.compressor = new Compressor(compressionStrategy);
        } else {
            this.compressor = null;
        }
    }

    public void setBodySizeLimits(BodySizeLimits bodySizeLimits) {
        this.bodySizeLimits = bodySizeLimits;
    }

    /**
     * Enable or disable automatic request content decompression in the Netty HTTP server.
     * When disabled, the server will not decode Content-Encoding or Transfer-Encoding on requests
     * and will pass the compressed body and headers through unchanged.
     * Default: true.
     *
     * @param requestDecompressionEnabled true to enable decompression, false to disable it
     */
    public void setRequestDecompressionEnabled(boolean requestDecompressionEnabled) {
        this.requestDecompressionEnabled = requestDecompressionEnabled;
    }

    /**
     * Whether the body of the current request is streamed to its consumer, which still reads it.
     * Event loop only.
     *
     * @return {@code true} while the request body is read by the application
     * @since 5.2.21
     */
    public boolean isStreamingRequestBody() {
        return inbound.state == InboundState.STREAMING;
    }

    /**
     * Run a task once the application discards the body of the current request, instead of
     * reading it to the end. Event loop only.
     *
     * @param listener The task
     * @since 5.2.21
     */
    public void onRequestBodyDiscarded(Runnable listener) {
        requestBodyDiscardListener = listener;
    }

    public static boolean canHaveBody(HttpResponseStatus status) {
        // All 1xx (Informational), 204 (No Content), and 304 (Not Modified)
        // responses do not include a message body. Compare codes: a status with a custom reason
        // phrase is not the canonical HttpResponseStatus constant.
        int code = status.code();
        return !(code >= 100 && code < 200 || code == 204 || code == 304);
    }

    /**
     * Declare that this connection will be closed after the given response, on a message that has
     * not been sent yet. Any {@code connection} header the response already carries is replaced,
     * so that the message never contains contradictory directives.
     *
     * @param message The response message to add the connection header to
     */
    private static void addConnectionClose(HttpResponse message) {
        // for a version where keep-alive is the default this sets `connection: close`, for one
        // where closing is the default it removes a contradicting `connection: keep-alive`
        HttpUtil.setKeepAlive(message, false);
    }

    /**
     * Pass a request to the {@link RequestHandler}. If the handler fails to accept it, the request
     * would never be answered, and any requests pipelined after it would wait for it forever, so
     * it is answered with an empty 500 response and the connection is closed after that.
     *
     * @param request        The request
     * @param body           The request body
     * @param outboundAccess The outbound access for the request
     */
    private void accept(HttpRequest request, CloseableByteBody body, OutboundAccessImpl outboundAccess) {
        try {
            requestHandler.accept(requiredCtx(), request, body, outboundAccess);
        } catch (Exception e) {
            try {
                body.close();
            } catch (RuntimeException closeError) {
                e.addSuppressed(closeError);
            }
            try {
                requestHandler.handleUnboundError(e);
            } catch (RuntimeException handlerError) {
                LOG.debug("Failed to handle an error from the request handler", handlerError);
            }
            rejectUnaccepted(outboundAccess);
        }
    }

    /**
     * Answer a request that the {@link RequestHandler} failed to accept and close the connection
     * once the responses of this and any earlier pipelined requests have been written.
     *
     * @param outboundAccess The outbound access of the failed request
     */
    private void rejectUnaccepted(OutboundAccessImpl outboundAccess) {
        if (outboundAccess.responseStarted()) {
            // the handler already wrote a response before failing
            if (outboundQueue.contains(outboundAccess) || (outboundHandler != null && outboundHandler.outboundAccess == outboundAccess)) {
                // it is still queued or being written, close once it is done
                outboundAccess.closeAfterWriteInEventLoop();
            } else {
                // it has been written completely already, close once it is flushed
                requiredCtx().writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
            return;
        }
        try {
            outboundAccess.closeAfterWrite();
            outboundAccess.write(new DefaultHttpResponse(outboundAccess.request.protocolVersion(), HttpResponseStatus.INTERNAL_SERVER_ERROR), NettyByteBodyFactory.empty());
        } catch (RuntimeException e) {
            LOG.debug("Failed to write the error response for a request that was not accepted", e);
            requiredCtx().close();
        }
    }

    private static boolean hasBody(HttpRequest request) {
        // if there's a decoder failure (e.g. invalid header), don't expect the body to come in
        if (request.decoderResult().isFailure()) {
            return false;
        }
        // Http requests don't have a body if they define 0 content length, or no content length and no transfer
        // encoding
        int contentLength;
        try {
            contentLength = HttpUtil.getContentLength(request, 0);
        } catch (NumberFormatException e) {
            // handle invalid content length, https://github.com/netty/netty/issues/12113
            contentLength = 0;
        }
        return contentLength != 0 || HttpUtil.isTransferEncodingChunked(request);
    }

    /**
     * Call {@code ctx.read()} if necessary.
     */
    private void refreshNeedMore() {
        assert ctx != null;
        // if readCalled is true, ctx.read() is already called and we haven't seen the associated readComplete yet.

        // needMore is false if there is downstream backpressure.

        // requestHandler itself (i.e. non-streaming request processing) does not have
        // backpressure. For this, check whether there is a request that has been fully read but
        // has no response yet. If there is, apply backpressure.
        if (!readCalled && outboundQueue.size() <= 1 && inbound.handler().needMore()) {
            readCalled = true;
            requiredCtx().read();
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        // we take control of reading now.
        ctx.channel().config().setAutoRead(false);
        refreshNeedMore();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        removed = true;
        if (outboundHandler != null) {
            outboundHandler.discardOutbound();
        }
        for (OutboundAccessImpl queued : outboundQueue) {
            if (queued.handler != null) {
                queued.handler.discardOutbound();
            }
        }
        inbound.discard();
        outboundQueue.clear();
        requestHandler.removed();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        reading = true;
        if (inbound.state == InboundState.MESSAGE && msg instanceof HttpRequest) {
            inbound.requestId++;
        }
        long requestId = inbound.requestId; // A callback may start the next request before this read returns.
        try {
            if (inbound.state != InboundState.MESSAGE && msg instanceof LastHttpContent content && !(msg instanceof HttpRequest) && content.decoderResult().isFailure()) {
                handleContentDecoderFailure(content);
            } else {
                inbound.read(msg);
            }
        } catch (Exception e) {
            inbound.handleUpstreamError(e);
        } finally {
            if (msg instanceof LastHttpContent && inbound.requestId == requestId) {
                inbound.endBody(true, null);
            }
        }
    }

    /**
     * Close the connection after a body chunk that the HTTP decoder could not parse, e.g. because
     * of a malformed chunk header or trailer. The decoder marks the (empty) last content of the
     * request as failed and discards all further input, so the connection cannot be reused.
     * Connections with request-level failures are closed by the {@link RequestHandler} (see
     * {@code RoutingInBoundHandler}) instead.
     */
    private void closeAfterContentDecoderFailure() {
        // close the connection once the response to the current request has been written
        OutboundAccessImpl lastResponse = outboundQueue.peekLast();
        if (lastResponse == null && outboundHandler != null) {
            lastResponse = outboundHandler.outboundAccess;
        }
        if (lastResponse == null) {
            // all responses have been handed to the channel already, but they may not be flushed
            // or fully sent yet. Close once they are.
            requiredCtx().writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        } else {
            lastResponse.closeAfterWriteInEventLoop();
        }
    }

    /**
     * Fail and close after an unparseable last chunk.
     *
     * @param content The failed content, released by this method
     */
    private void handleContentDecoderFailure(HttpContent content) {
        Throwable cause = content.decoderResult().cause();
        content.release();
        // same status mapping as for request-level decoder failures in NettyRequestLifecycle
        HttpStatus status = cause instanceof TooLongFrameException ? HttpStatus.REQUEST_ENTITY_TOO_LARGE : HttpStatus.BAD_REQUEST;
        HttpStatusException failure = new HttpStatusException(status, "Malformed request body");
        failure.initCause(cause);
        inbound.endBody(true, failure, true);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        inbound.handler().readComplete();
        reading = false;
        // only unset readCalled now. This ensures no read call is done before channelReadComplete
        readCalled = false;
        if (flushPending) {
            ctx.flush();
            flushPending = false;
        }
        refreshNeedMore();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        inbound.handleUpstreamError(cause);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        if (quic) {
            // QUIC can briefly report writable while draining its write queue, before updating its
            // remaining capacity. Wait for that update before requesting more response content.
            if (!quicWritePending) {
                quicWritePending = true;
                ctx.executor().execute(() -> {
                    quicWritePending = false;
                    writeSome();
                });
            }
        } else {
            writeSome();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent idleStateEvent) {
            IdleState state = idleStateEvent.state();
            if (state == IdleState.ALL_IDLE) {
                ctx.close();
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    /**
     * Write a message.
     *
     * @param message The message to write
     * @param flush   {@code true} iff we should flush after this message
     * @param close   {@code true} iff the channel should be closed after this message
     */
    private ChannelFuture write(Object message, boolean flush, boolean close, boolean needsPromise) {
        assert ctx != null;
        if (close) {
            return requiredCtx().writeAndFlush(message)
                .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE)
                .addListener(ChannelFutureListener.CLOSE);
        } else {
            ChannelPromise promise = needsPromise ?
                requiredCtx().newPromise().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE) :
                requiredCtx().voidPromise();
            if (flush) {
                // delay flush until readComplete if possible
                if (reading) {
                    requiredCtx().write(message, promise);
                    flushPending = true;
                    return promise;
                } else {
                    return requiredCtx().writeAndFlush(message, promise);
                }
            } else {
                return requiredCtx().write(message, promise);
            }
        }
    }

    /**
     * Write some data if possible.
     */
    private void writeSome() {
        assert ctx != null;
        if (writing) {
            // already inside writeSome
            return;
        }
        writing = true;
        try {
            while (!removed && requiredCtx().channel().isWritable()) {
                // if we have no outboundHandler, check whether the first queued response is ready
                if (outboundHandler == null) {
                    OutboundAccessImpl next = outboundQueue.peek();
                    if (next != null && next.handler != null) {
                        outboundQueue.poll();
                        outboundHandler = next.handler;
                        outboundHandler.activate();
                        refreshNeedMore();
                    } else {
                        return;
                    }
                }
                if (removed || outboundHandler == null) {
                    continue;
                }
                OutboundHandler oldHandler = outboundHandler;
                oldHandler.writeSome();
                if (outboundHandler == oldHandler) {
                    // handler is not done yet
                    break;
                }
            }
        } finally {
            writing = false;
        }
    }

    @Override
    public OptionalLong reportActiveTasks() {
        return OptionalLong.of(1);
    }

    @SuppressWarnings("resource")
    @Override
    public CompletionStage<?> shutdownGracefully() {
        assert ctx != null;
        if (requiredCtx().executor().inEventLoop()) {
            shutdownGracefully0();
        } else {
            requiredCtx().executor().execute(this::shutdownGracefully0);
        }

        return NettyHttpServer.toCompletionStage(requiredCtx().channel().closeFuture());
    }

    private void shutdownGracefully0() {
        assert ctx != null;
        /*
         * - if there is no in-flight request, close immediately.
         * - if there is one or more request where the response headers have not been sent yet, send `connection: close` for the last of them
         * - if there is one or more request, close the connection after the last response
         */
        shuttingDown = true;
        if (inbound.state == InboundState.MESSAGE && outboundHandler == null && outboundQueue.isEmpty()) {
            requiredCtx().close();
        } else {
            OutboundAccessImpl lastResponse = outboundQueue.peekLast();
            if (lastResponse == null && outboundHandler != null) {
                // the last response is the one that is being written right now
                lastResponse = outboundHandler.outboundAccess;
            }
            if (lastResponse != null) {
                lastResponse.closeAfterWriteInEventLoop();
            }
        }
    }

    private NettyByteBodyFactory byteBodyFactory() {
        assert ctx != null;
        return new NettyByteBodyFactory(requiredCtx().channel());
    }

    /**
     * Assemble the pieces of a request body that arrived before {@code channelReadComplete} into
     * one buffer. All pieces are added at once so that the composite consolidates (copies the
     * pieces into one contiguous buffer) at most once, and only beyond
     * {@link #MAX_COMPOSITE_COMPONENTS} pieces; adding them one at a time consolidates after every
     * 16th piece, copying everything accumulated so far each time. Ownership of the pieces
     * transfers to the returned buffer.
     *
     * @param alloc  The allocator
     * @param pieces The pieces, at least two
     * @return The composed body
     */
    static ByteBuf composeBody(ByteBufAllocator alloc, List<ByteBuf> pieces) {
        CompositeByteBuf composite = alloc.compositeBuffer(MAX_COMPOSITE_COMPONENTS);
        boolean added = false;
        try {
            // addComponents takes ownership of all pieces, releasing any it did not add
            composite.addComponents(true, pieces);
            added = true;
            return composite;
        } finally {
            if (!added) {
                composite.release();
            }
        }
    }

    private enum InboundState {
        MESSAGE,
        BUFFERING,
        STREAMING,
        DROPPING
    }

    private final class InboundContext {
        private InboundState state = InboundState.MESSAGE;
        private long requestId;
        @Nullable
        private StreamingInboundHandler streaming;
        @Nullable
        private DecompressingInboundHandler decompressor;

        private InboundHandler handler() {
            return switch (state) {
                case MESSAGE -> baseInboundHandler;
                case BUFFERING -> optimisticBufferingInboundHandler;
                case STREAMING -> Objects.requireNonNull(streaming);
                case DROPPING -> droppingInboundHandler;
            };
        }

        private void read(Object message) {
            if (decompressor == null) {
                handler().read(message);
            } else {
                decompressor.read(message);
            }
        }

        /**
         * End body delivery once, or finish draining an already ended body on its last content.
         * Commit all state before disposing the decoder or notifying the body/application.
         */
        private boolean endBody(boolean last, @Nullable Throwable failure) {
            return endBody(last, failure, false);
        }

        private boolean endBody(boolean last, @Nullable Throwable failure, boolean close) {
            if (state == InboundState.MESSAGE || (!last && state == InboundState.DROPPING)) {
                return false;
            }
            InboundHandler handler = handler();
            InboundState previous = state;
            DecompressingInboundHandler decompressor = this.decompressor;
            state = last ? InboundState.MESSAGE : InboundState.DROPPING;
            streaming = null;
            this.decompressor = null;
            requestBodyDiscardListener = null;
            if (close) {
                closeAfterContentDecoderFailure();
            }
            if (decompressor != null) {
                decompressor.dispose();
            }
            if (failure != null && previous != InboundState.DROPPING) {
                handler.handleUpstreamError(failure);
            } else if (last) {
                try {
                    handler.complete();
                } catch (Exception e) {
                    if (previous == InboundState.STREAMING) {
                        handler.handleUpstreamError(e);
                    } else {
                        requestHandler.handleUnboundError(e);
                    }
                }
            }
            return true;
        }

        private void handleUpstreamError(Throwable cause) {
            if (state == InboundState.MESSAGE || state == InboundState.DROPPING) {
                requestHandler.handleUnboundError(cause);
            } else {
                endBody(false, cause);
            }
        }

        private void discard() {
            InboundHandler handler = handler();
            if (endBody(false, null)) {
                handler.discard();
            }
        }
    }

    /**
     * An inbound handler is responsible for all incoming messages.
     */
    private abstract static class InboundHandler {
        /**
         * @return {@code true} iff this handler can process more data. This is usually {@code true},
         * except for streaming requests when there is downstream backpressure.
         */
        boolean needMore() {
            return true;
        }

        abstract void read(Object message);

        void complete() {
        }

        abstract void handleUpstreamError(Throwable cause);

        void readComplete() {
        }

        void discard() {
        }
    }

    /**
     * Base {@link InboundHandler} that handles {@link HttpRequest}s and then determines how to
     * deal with the body.
     */
    private final class MessageInboundHandler extends InboundHandler {
        @Override
        void read(Object message) {
            assert ctx != null;
            if (!(message instanceof HttpRequest request)) {
                // content without a request. HttpObjectDecoder does not produce this, but it may
                // come from a custom pipeline handler
                if (message instanceof LastHttpContent last && last.decoderResult().isFailure()) {
                    closeAfterContentDecoderFailure();
                }
                ReferenceCountUtil.release(message);
                return;
            }
            OutboundAccessImpl outboundAccess = new OutboundAccessImpl(request);
            outboundQueue.add(outboundAccess);

            EmbeddedChannel decompressionChannel;
            ZlibBodyDecoder zlibDecoder = null;
            if (requestDecompressionEnabled) {
                HttpHeaders headers = request.headers();
                String contentEncoding = getContentEncoding(headers);
                if (contentEncoding == null) {
                    decompressionChannel = null;
                } else if (HttpHeaderValues.GZIP.contentEqualsIgnoreCase(contentEncoding) ||
                    HttpHeaderValues.X_GZIP.contentEqualsIgnoreCase(contentEncoding)) {
                    zlibDecoder = new ZlibBodyDecoder(ZlibWrapper.GZIP, maxZlibAllocation());
                    decompressionChannel = newDecompressionChannel(zlibDecoder);
                } else if (HttpHeaderValues.DEFLATE.contentEqualsIgnoreCase(contentEncoding) ||
                    HttpHeaderValues.X_DEFLATE.contentEqualsIgnoreCase(contentEncoding)) {
                    zlibDecoder = new ZlibBodyDecoder(ZlibWrapper.ZLIB_OR_NONE, maxZlibAllocation());
                    decompressionChannel = newDecompressionChannel(zlibDecoder);
                } else if (Brotli.isAvailable() && HttpHeaderValues.BR.contentEqualsIgnoreCase(contentEncoding)) {
                    decompressionChannel = newDecompressionChannel(new BrotliDecoder());
                } else if (HttpHeaderValues.SNAPPY.contentEqualsIgnoreCase(contentEncoding)) {
                    decompressionChannel = newDecompressionChannel(new SnappyFrameDecoder());
                } else {
                    decompressionChannel = null;
                }
                if (decompressionChannel != null) {
                    headers.remove(HttpHeaderNames.CONTENT_LENGTH);
                    headers.remove(HttpHeaderNames.CONTENT_ENCODING);
                    headers.add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                }
            } else {
                decompressionChannel = null;
            }

            // getClass for performance
            boolean full = request.getClass() != DefaultHttpRequest.class && request instanceof FullHttpRequest;
            if (full && decompressionChannel == null) {
                inbound.state = InboundState.DROPPING;
                inbound.endBody(true, null);
                accept(request, byteBodyFactory().createChecked(bodySizeLimits, ((FullHttpRequest) request).content()), outboundAccess);
            } else if (!hasBody(request)) {
                inbound.state = InboundState.DROPPING;
                if (full) {
                    droppingInboundHandler.read(message);
                    inbound.endBody(true, null);
                }
                if (decompressionChannel != null) {
                    decompressionChannel.finish();
                }
                accept(request, NettyByteBodyFactory.empty(), outboundAccess);
            } else {
                optimisticBufferingInboundHandler.init(request, outboundAccess);
                inbound.state = InboundState.BUFFERING;
                if (decompressionChannel != null) {
                    inbound.decompressor = new DecompressingInboundHandler(decompressionChannel, zlibDecoder);
                }
                if (full) {
                    inbound.read(new DefaultLastHttpContent(((FullHttpRequest) request).content()));
                }
            }
        }

        private EmbeddedChannel newDecompressionChannel(ChannelHandler decompressor) {
            EmbeddedChannel channel = new EmbeddedChannel(requiredCtx().channel().id(),
                requiredCtx().channel().metadata().hasDisconnect(), requiredCtx().channel().config());
            channel.pipeline().addLast(DECOMPRESSOR_HANDLER, decompressor);
            return channel;
        }

        private int maxZlibAllocation() {
            long maxBufferSize = bodySizeLimits.maxBufferSize();
            if (maxBufferSize == Long.MAX_VALUE || maxBufferSize > Integer.MAX_VALUE) {
                return 0;
            }
            return (int) Math.max(1, maxBufferSize);
        }

        @Nullable
        private static String getContentEncoding(HttpHeaders headers) {
            // from io.netty.handler.codec.http.HttpContentDecoder

            // Determine the content encoding.
            String contentEncoding = headers.get(HttpHeaderNames.CONTENT_ENCODING);
            if (contentEncoding != null) {
                contentEncoding = contentEncoding.trim();
            } else {
                String transferEncoding = headers.get(HttpHeaderNames.TRANSFER_ENCODING);
                if (transferEncoding != null) {
                    int idx = transferEncoding.indexOf(",");
                    if (idx != -1) {
                        contentEncoding = transferEncoding.substring(0, idx).trim();
                    } else {
                        contentEncoding = transferEncoding.trim();
                    }
                }
            }
            return contentEncoding;
        }

        @Override
        void handleUpstreamError(Throwable cause) {
            requestHandler.handleUnboundError(cause);
        }
    }

    /**
     * Handler that buffers input data until the request is complete, in which case it forwards it
     * as {@link FullHttpRequest}, or devolves to {@link StreamingInboundHandler} if not all data
     * has arrived yet by the time {@link #channelReadComplete} is called.
     */
    private final class OptimisticBufferingInboundHandler extends InboundHandler {
        @Nullable
        private HttpRequest request;
        @Nullable
        private OutboundAccessImpl outboundAccess;
        private final List<HttpContent> buffer = new ArrayList<>();
        private long receivedLength;

        void init(HttpRequest request, OutboundAccessImpl outboundAccess) {
            assert buffer.isEmpty();
            assert receivedLength == 0;
            this.request = request;
            this.outboundAccess = outboundAccess;
        }

        @Override
        void read(Object message) {
            HttpContent content = (HttpContent) message;
            if (content.content().isReadable()) {
                int readableBytes = content.content().readableBytes();
                if (bodySizeLimits.maxBodySize() != Long.MAX_VALUE &&
                    readableBytes > bodySizeLimits.maxBodySize() - receivedLength) {
                    long actualLength = receivedLength > Long.MAX_VALUE - readableBytes ?
                        Long.MAX_VALUE : receivedLength + readableBytes;
                    ContentLengthExceededException failure =
                        new ContentLengthExceededException(bodySizeLimits.maxBodySize(), actualLength);
                    content.release();
                    inbound.endBody(false, failure);
                    return;
                }
                receivedLength += readableBytes;
                buffer.add(content);
            } else {
                content.release();
            }
        }

        @Override
        void complete() {
            // we got the full message before readComplete
            ByteBuf fullBody;
            if (buffer.isEmpty()) {
                fullBody = Unpooled.EMPTY_BUFFER;
            } else if (buffer.size() == 1) {
                fullBody = buffer.getFirst().content();
            } else {
                List<ByteBuf> pieces = new ArrayList<>(buffer.size());
                for (HttpContent c : buffer) {
                    pieces.add(c.content());
                }
                // composeBody takes ownership of the pieces even when it fails, so the
                // messages must not be reachable for a later devolveToStreaming or discard
                buffer.clear();
                fullBody = composeBody(requiredCtx().alloc(), pieces);
            }
            buffer.clear();
            receivedLength = 0;
            HttpRequest request = this.request;
            this.request = null;
            OutboundAccessImpl outboundAccess = this.outboundAccess;
            this.outboundAccess = null;
            assert ctx != null;
            assert request != null;
            assert outboundAccess != null;
            CloseableByteBody body = byteBodyFactory().createChecked(bodySizeLimits, fullBody);
            accept(Objects.requireNonNull(request), body, Objects.requireNonNull(outboundAccess));
        }

        @Override
        void readComplete() {
            devolveToStreaming(true);
            inbound.handler().readComplete();
        }

        @Override
        void handleUpstreamError(Throwable cause) {
            StreamingInboundHandler streaming = devolveToStreaming(false);
            if (streaming.discarded) {
                requestHandler.handleUnboundError(cause);
            } else {
                streaming.handleUpstreamError(cause);
            }
        }

        private StreamingInboundHandler devolveToStreaming(boolean install) {
            HttpRequest request = Objects.requireNonNull(this.request);
            OutboundAccessImpl outboundAccess = Objects.requireNonNull(this.outboundAccess);
            this.request = null;
            this.outboundAccess = null;

            StreamingInboundHandler streamingInboundHandler = new StreamingInboundHandler(Objects.requireNonNull(outboundAccess), HttpUtil.is100ContinueExpected(request));
            if (install) {
                inbound.streaming = streamingInboundHandler;
                inbound.state = InboundState.STREAMING;
            }
            // install the handler first: if the Content-Length already exceeds the limit, this
            // rejects the body and switches to the dropping handler so the rest is drained
            streamingInboundHandler.dest.setExpectedLengthFrom(request.headers());
            for (HttpContent content : buffer) {
                streamingInboundHandler.read(content);
            }
            buffer.clear();
            receivedLength = 0;

            accept(request, new StreamingNettyByteBody(streamingInboundHandler.dest), outboundAccess);
            return streamingInboundHandler;
        }

        @Override
        void discard() {
            for (HttpContent content : buffer) {
                content.release();
            }
            buffer.clear();
            receivedLength = 0;
        }
    }

    /**
     * Handler that exposes incoming content as a {@link Flux}.
     */
    private final class StreamingInboundHandler extends InboundHandler implements BufferConsumer.Upstream {
        final StreamingNettyByteBody.SharedBuffer dest;
        final OutboundAccessImpl outboundAccess;
        long requested = 65535; // This is the number of bytes we initially accept before any downstream demand. 65535 matches the INITIAL_WINDOW_SIZE of HTTP/2.
        boolean sendContinue;
        boolean discarded;

        private StreamingInboundHandler(OutboundAccessImpl outboundAccess, boolean sendContinue) {
            this.outboundAccess = outboundAccess;
            this.sendContinue = sendContinue;
            this.dest = byteBodyFactory().createStreamingBuffer(bodySizeLimits, this);
        }

        @Override
        void read(Object message) {
            HttpContent content = (HttpContent) message;
            requested -= content.content().readableBytes();
            dest.add(byteBodyFactory().readBufferFactory().adapt(content.content()));
        }

        @Override
        void complete() {
            dest.complete();
        }

        @Override
        void discard() {
            // note: this has to match RoutingInBoundHandler#IGNORABLE_ERROR_MESSAGE
            handleUpstreamError(new EOFException("Connection closed before full body was received"));
        }

        @Override
        void handleUpstreamError(Throwable cause) {
            dest.error(cause);
        }

        @Override
        boolean needMore() {
            return requested > 0;
        }

        @Override
        public void start() {
            assert ctx != null;
            EventLoop eventLoop = requiredCtx().channel().eventLoop();
            if (!eventLoop.inEventLoop()) {
                eventLoop.execute(this::start);
                return;
            }

            if (sendContinue) {
                sendContinue = false;
                outboundAccess.writeContinue();
            }
        }

        @Override
        public void onBytesConsumed(long bytesConsumed) {
            assert ctx != null;
            EventLoop eventLoop = requiredCtx().channel().eventLoop();
            if (!eventLoop.inEventLoop()) {
                eventLoop.execute(() -> onBytesConsumed(bytesConsumed));
                return;
            }

            long newRequested = requested + bytesConsumed;
            if (newRequested < requested) {
                // overflow
                newRequested = Long.MAX_VALUE;
            }
            requested = newRequested;
            refreshNeedMore();
        }

        @Override
        public void allowDiscard() {
            EventLoop eventLoop = requiredCtx().channel().eventLoop();
            if (!eventLoop.inEventLoop()) {
                eventLoop.execute(this::allowDiscard);
                return;
            }

            sendContinue = false;
            Runnable listener = requestBodyDiscardListener;
            boolean active = inbound.state == InboundState.STREAMING && inbound.streaming == this && inbound.endBody(false, null);
            if (active) {
                refreshNeedMore();
            }
            discarded = true;
            dest.discard();
            if (active && listener != null) {
                listener.run();
            }
        }

        @Override
        public void disregardBackpressure() {
            EventLoop eventLoop = requiredCtx().channel().eventLoop();
            if (!eventLoop.inEventLoop()) {
                eventLoop.execute(this::disregardBackpressure);
                return;
            }

            requested = Long.MAX_VALUE;
            refreshNeedMore();
        }
    }

    private final class DecompressingInboundHandler {
        private final EmbeddedChannel channel;
        @Nullable
        private final ZlibBodyDecoder zlibDecoder;

        private DecompressingInboundHandler(EmbeddedChannel channel, @Nullable ZlibBodyDecoder zlibDecoder) {
            this.channel = channel;
            this.zlibDecoder = zlibDecoder;

            channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    ByteBuf decompressed = (ByteBuf) msg;
                    if (inbound.decompressor != DecompressingInboundHandler.this || !decompressed.isReadable()) {
                        decompressed.release();
                        return;
                    }
                    inbound.handler().read(new DefaultHttpContent(decompressed));
                }
            });
        }

        void read(Object message) {
            HttpContent content = (HttpContent) message;
            boolean last = message instanceof LastHttpContent;

            ByteBuf compressed = content.content();
            if (!compressed.isReadable() && !last) {
                inbound.handler().read(message);
                return;
            }

            channel.writeInbound(compressed);
            if (last && inbound.decompressor == this) {
                if (zlibDecoder != null && zlibDecoder.isTruncated()) {
                    throw new DecompressionException("Request body ended before the end of the compressed stream");
                }
                if (channel.isOpen()) {
                    channel.finish();
                }
            }
        }

        void dispose() {
            if (channel.pipeline().context(DECOMPRESSOR_HANDLER) != null) {
                // Removing the handler cancels any decompression currently in progress.
                channel.pipeline().remove(DECOMPRESSOR_HANDLER);
            }
            if (!channel.isOpen()) {
                return;
            }
            try {
                channel.finishAndReleaseAll();
            } catch (DecompressionException ignored) {
            }
        }
    }

    /**
     * Handler that drops all incoming content.
     */
    private final class DroppingInboundHandler extends InboundHandler {
        @Override
        void read(Object message) {
            ((HttpContent) message).release();
        }

        @Override
        void handleUpstreamError(Throwable cause) {
            requestHandler.handleUnboundError(cause);
        }
    }

    private enum Answered {
        NONE,
        CONTINUE_ONLY,
        RESPONSE,
        DONE
    }

    /**
     * Class that allows writing the response for the request this object is associated with.
     */
    public final class OutboundAccessImpl implements OutboundAccess {
        /**
         * The request that caused this response. This is used for compression decisions.
         */
        private final HttpRequest request;
        @Nullable
        private final Http1RequestEvent jfrEvent;
        /**
         * The handler that will perform the actual write operation.
         */
        @Nullable
        private volatile OutboundHandler handler;
        private volatile Answered answered = Answered.NONE;
        @Nullable
        private Object attachment = null;
        private volatile boolean closeAfterWrite = false;

        private OutboundAccessImpl(HttpRequest request) {
            this.request = request;
            if (NativeImageUtils.JFR_AVAILABLE && Http1RequestEvent.isTurnedOn()) {
                this.jfrEvent = new Http1RequestEvent();
                jfrEvent.begin();
            } else {
                this.jfrEvent = null;
            }
        }

        /**
         * Set an attachment that is passed to {@link RequestHandler#responseWritten}. Defaults to
         * {@code null}.
         *
         * @param attachment The attachment to forward
         */
        @Override
        public void attachment(Object attachment) {
            this.attachment = attachment;
        }

        /**
         * Mark this channel to be closed after this response has been written.
         */
        @Override
        public void closeAfterWrite() {
            closeAfterWrite = true;
        }

        /**
         * Mark this channel to be closed after this response has been written, from inside the
         * event loop. Unlike {@link #closeAfterWrite()} this also adds the {@code connection}
         * header to the response if {@link #preprocess} has already run for it.
         */
        private void closeAfterWriteInEventLoop() {
            closeAfterWrite = true;
            OutboundHandler current = this.handler;
            if (current != null) {
                // the response has already been prepared, so preprocess did not see this flag. The
                // message has not been written yet though, so we can still add the header.
                current.markCloseAfterWrite();
            }
        }

        private void preprocess(HttpResponse message) {
            if (!message.protocolVersion().equals(request.protocolVersion())) {
                // if the response includes features not supported by http/1.0, well that's just too bad, isn't it?
                // we'll at least handle the connection state properly.
                message.setProtocolVersion(request.protocolVersion());
            }
            if (request.protocolVersion().isKeepAliveDefault()) {
                if (request.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true)) {
                    closeAfterWrite();
                }
            } else {
                if (!request.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE, true)) {
                    closeAfterWrite();
                }
            }

            if (message.protocolVersion().isKeepAliveDefault()) {
                if (message.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true)) {
                    closeAfterWrite();
                } else if (closeAfterWrite) {
                    // add the header
                    message.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
                }
            } else {
                if (!message.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE, true)) {
                    closeAfterWrite();
                } else if (closeAfterWrite) {
                    // remove the keep-alive header
                    message.headers().remove(HttpHeaderNames.CONNECTION);
                }
            }
            // According to RFC 7230 a server MUST NOT send a Content-Length or a Transfer-Encoding when the status
            // code is 1xx or 204, also a status code 304 may not have a Content-Length or Transfer-Encoding set.
            if (!HttpUtil.isContentLengthSet(message) && !HttpUtil.isTransferEncodingChunked(message) && canHaveBody(message.status())) {
                HttpUtil.setKeepAlive(message, false);
                closeAfterWrite();
            }

            if (jfrEvent != null) {
                jfrEvent.populateResponse(message);
            }
        }

        /**
         * Write a 100 CONTINUE response.
         */
        private void writeContinue() {
            if (answered == Answered.NONE) {
                write(new ContinueOutboundHandler(this));
            }
        }

        /**
         * Whether a response (other than a {@code 100 Continue}) has been submitted for this
         * request.
         */
        private boolean responseStarted() {
            return answered == Answered.RESPONSE || answered == Answered.DONE;
        }

        /**
         * Write a response using the given outbound handler, when ready.
         */
        private void write(OutboundHandler handler) {
            if (responseStarted()) {
                // the refused response is not written: release what it holds
                handler.discardRefused();
                throw new IllegalStateException("Only one response per request");
            }

            EventLoop eventLoop = requiredCtx().channel().eventLoop();
            if (!eventLoop.inEventLoop()) {
                eventLoop.execute(() -> write(handler));
                return;
            }

            answered = handler instanceof ContinueOutboundHandler ? Answered.CONTINUE_ONLY : Answered.RESPONSE;
            if (removed) {
                handler.discardOutbound();
                return;
            }

            if (this.handler instanceof ContinueOutboundHandler cont) {
                cont.next = handler;
                writeSome();
            } else {
                this.handler = handler;
                if (outboundQueue.peek() == this) {
                    writeSome();
                }
            }
        }

        @Override
        public void abort() {
            EventLoop eventLoop = requiredCtx().channel().eventLoop();
            if (!eventLoop.inEventLoop()) {
                eventLoop.execute(this::abort);
                return;
            }
            if (removed || answered == Answered.DONE) {
                return;
            }
            // the response may be cut: no other response can follow it on this connection.
            // Closing discards the outbound handlers that are left
            requiredCtx().close();
        }

        @Override
        public void writeHeadResponse(HttpResponse response) {
            writeFull(new DefaultFullHttpResponse(
                response.protocolVersion(),
                response.status(),
                Unpooled.EMPTY_BUFFER,
                response.headers(),
                EmptyHttpHeaders.INSTANCE
            ), true);
        }

        private void writeFull(FullHttpResponse response, boolean headResponse) {
            FullOutboundHandler oh;
            try {
                response.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
                if (canHaveBody(response.status())) {
                    if (!headResponse) {
                        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
                    }
                } else {
                    response.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
                }
                preprocess(response);
                oh = new FullOutboundHandler(this, response);
                if (response.content().isReadable()) {
                    prepareCompression(response, oh, response.content().readableBytes());
                }
            } catch (Throwable t) {
                // the response never reached the connection
                response.release();
                throw t;
            }
            write(oh);
        }

        @Override
        public void write(HttpResponse response, ByteBody body) {
            if (body instanceof AvailableByteBody available) {
                writeFull(new DefaultFullHttpResponse(response.protocolVersion(), response.status(), NettyByteBodyFactory.toByteBuf(available), response.headers(), EmptyHttpHeaders.INSTANCE), false);
            } else {
                OptionalLong expectedLength = body.expectedLength();
                if (expectedLength.isPresent()) {
                    response.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
                    if (canHaveBody(response.status())) {
                        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, expectedLength.getAsLong());
                    } else {
                        response.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
                    }
                } else {
                    response.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
                    if (canHaveBody(response.status()) && !request.protocolVersion().equals(HttpVersion.HTTP_1_0)) {
                        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                    } else {
                        // HTTP/1.0 has no chunked coding: the end of the connection ends the body
                        response.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
                    }
                }
                preprocess(response);
                StreamingOutboundHandler oh = new StreamingOutboundHandler(this, response);
                prepareCompression(response, oh, expectedLength.orElse(-1));
                oh.upstream = byteBodyFactory().toStreaming(body).primary(oh);
                write(oh);
            }
        }

        private void prepareCompression(HttpResponse response, OutboundHandler outboundHandler, long contentLength) {
            if (compressor == null || responseStarted()) {
                return;
            }
            assert ctx != null;
            Compressor.Session compressionSession = compressor.prepare(requiredCtx(), request, response, contentLength);
            if (compressionSession != null) {
                // if content-length and transfer-encoding are unset, we will close anyway.
                // if this is a full response, there's special handling below in OutboundHandler
                if (!(response instanceof FullHttpResponse) && response.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
                    response.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
                    response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                }
                outboundHandler.compressionSession = compressionSession;
            }
        }
    }

    private enum ResponsePhase {
        QUEUED,
        HEADERS_PENDING,
        WRITING_HEADERS,
        STREAMING,
        DONE
    }

    private enum TerminalOutcome {
        WRITTEN,
        DISCARDED,
        REFUSED,
        CONTINUED
    }

    private enum PendingOutcome {
        OPEN,
        COMPLETE,
        FAILED
    }

    private abstract class OutboundHandler {
        /**
         * {@link OutboundAccessImpl} that created this handler, for metadata access.
         */
        final OutboundAccessImpl outboundAccess;

        Compressor. @Nullable Session compressionSession;

        private ResponsePhase phase = ResponsePhase.QUEUED;
        @Nullable
        private TerminalOutcome terminalOutcome;

        private OutboundHandler(OutboundAccessImpl outboundAccess) {
            this.outboundAccess = outboundAccess;
        }

        final ResponsePhase getPhase() {
            return phase;
        }

        @SuppressWarnings("checkstyle:MissingSwitchDefault") // List every legal transition.
        final void transition(ResponsePhase from, ResponsePhase to) {
            boolean allowed = switch (from) {
                case QUEUED -> to == ResponsePhase.HEADERS_PENDING || to == ResponsePhase.DONE;
                case HEADERS_PENDING -> to == ResponsePhase.WRITING_HEADERS || to == ResponsePhase.DONE;
                case WRITING_HEADERS -> to == ResponsePhase.STREAMING || to == ResponsePhase.DONE;
                case STREAMING -> to == ResponsePhase.DONE;
                case DONE -> false;
            };
            if (phase != from || !allowed) {
                throw new IllegalStateException("Illegal response transition: " + phase + " -> " + to + " (expected " + from + ")");
            }
            phase = to;
        }

        final void activate() {
            assert requiredCtx().executor().inEventLoop();
            transition(ResponsePhase.QUEUED, ResponsePhase.HEADERS_PENDING);
        }

        final void finish(HttpContent last) {
            if (terminate(last, TerminalOutcome.WRITTEN)) {
                PipeliningServerHandler.this.writeSome();
            }
        }

        /**
         * End an accepted response exactly once, notifying before releasing discarded data.
         */
        private boolean terminate(@Nullable HttpContent last, TerminalOutcome outcome) {
            assert requiredCtx().executor().inEventLoop();
            if (terminalOutcome != null) {
                return false;
            }
            transition(phase, ResponsePhase.DONE);
            terminalOutcome = outcome;
            boolean responseFinished = outcome == TerminalOutcome.WRITTEN || outcome == TerminalOutcome.DISCARDED;
            if (responseFinished) {
                outboundAccess.answered = Answered.DONE;
            }
            if (outboundHandler == this) {
                outboundHandler = null;
            }
            // Terminal writes and cleanup callbacks can reenter the connection's queue pump.
            boolean wasWriting = writing;
            writing = true;
            try {
                try {
                    if (last != null) {
                        writeCompressing(last, true, true);
                    }
                } finally {
                    try {
                        if (responseFinished) {
                            requestHandler.responseWritten(outboundAccess.attachment);
                        }
                    } finally {
                        // Preserve notification before allowDiscard/release on the discard path.
                        if (last == null) {
                            discardResources();
                        }
                    }
                }
            } catch (Throwable t) {
                try {
                    if (last != null) {
                        discardResources();
                    }
                } catch (Throwable cleanupError) {
                    t.addSuppressed(cleanupError);
                } finally {
                    requiredCtx().close();
                }
                throw t;
            } finally {
                writing = wasWriting;
            }
            return true;
        }

        /**
         * Retire the informational writer without completing the request's final response.
         */
        final void retireContinue() {
            terminate(null, TerminalOutcome.CONTINUED);
        }

        private boolean shouldCloseAfterContent(boolean last) {
            return last && (outboundAccess.closeAfterWrite || (shuttingDown && outboundQueue.isEmpty()));
        }

        protected final void writeCompressing(HttpContent content, @SuppressWarnings("SameParameterValue") boolean flush, boolean last) {
            if (this.compressionSession == null) {
                writePotentialEnd(content, flush, shouldCloseAfterContent(last));
            } else {
                // slow path
                writeCompressing0(content, flush, last);
            }
        }

        void writePotentialEnd(Object content, boolean flush, boolean close) {
            assert ctx != null;
            boolean record = outboundAccess.jfrEvent != null && content instanceof LastHttpContent;
            ChannelFuture future = write(content, flush, close, record);
            if (record) {
                future.addListener((ChannelFutureListener) f -> {
                    Http1RequestEvent jfrEvent = Objects.requireNonNull(outboundAccess.jfrEvent);
                    jfrEvent.end();
                    if (jfrEvent.shouldCommit()) {
                        jfrEvent.populateChannel(requiredCtx().channel());
                        jfrEvent.populateRequest(outboundAccess.request);
                        jfrEvent.commit();
                    }
                });
            }
        }

        private void writeCompressing0(HttpContent content, boolean flush, boolean last) {
            Compressor.Session compressionSession = Objects.requireNonNull(this.compressionSession);
            compressionSession.push(content.content());
            if (last) {
                compressionSession.finish();
            }
            if (content instanceof HttpResponse hr) {
                assert last;

                compressionSession.fixContentLength(hr);

                // this can happen in FullHttpResponse, just send the full body.
                write(new DefaultHttpResponse(hr.protocolVersion(), hr.status(), hr.headers()), false, false, false);
            }
            boolean close = shouldCloseAfterContent(last);
            ByteBuf toSend = compressionSession.poll();
            // send the compressed buffer with the flags.
            if (toSend == null) {
                if (last) {
                    HttpHeaders trailingHeaders = ((LastHttpContent) content).trailingHeaders();
                    writePotentialEnd(trailingHeaders.isEmpty() ? LastHttpContent.EMPTY_LAST_CONTENT : new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER, trailingHeaders), flush, close);
                } else if (flush || close) {
                    // not sure if this can actually happen, but we need to forward a flush/close
                    write(new DefaultHttpContent(Unpooled.EMPTY_BUFFER), flush, close, false);
                } // else just don't send anything
            } else {
                if (last) {
                    writePotentialEnd(new DefaultLastHttpContent(toSend, ((LastHttpContent) content).trailingHeaders()), flush, close);
                } else {
                    write(new DefaultHttpContent(toSend), flush, close, false);
                }
            }
        }

        /**
         * Write some data to the channel.
         */
        abstract void writeSome();

        /**
         * The connection will be closed after this response. Add the {@code connection} header to
         * the response message, unless it has been sent already.
         */
        abstract void markCloseAfterWrite();

        /**
         * Discard the remaining data.
         */
        void discardOutbound() {
            terminate(null, TerminalOutcome.DISCARDED);
        }

        void discardResources() {
            Compressor.Session compressionSession = this.compressionSession;
            this.compressionSession = null;
            if (compressionSession != null) {
                compressionSession.discard();
            }
        }

        /**
         * Release the data of this response, which is refused because another response was
         * written for the request already. Unlike {@link #discardOutbound()}, the request is not
         * marked as answered: the response that was accepted does that.
         */
        void discardRefused() {
            if (terminalOutcome == null) {
                transition(phase, ResponsePhase.DONE);
                terminalOutcome = TerminalOutcome.REFUSED;
                discardResources();
            }
        }
    }

    private enum ContinueState {
        CONTINUE_PENDING,
        CONTINUE_SENT
    }

    /**
     * Handler that writes a 100 CONTINUE response and then proceeds with the {@link #next} handler.
     */
    final class ContinueOutboundHandler extends OutboundHandler {
        // there is no HTTP/1.0 equivalent: HttpUtil.is100ContinueExpected is always false for
        // HTTP/1.0, so a continue response is never requested for that version.
        static final FullHttpResponse CONTINUE_11 =
            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER);

        @Nullable
        volatile OutboundHandler next;
        private ContinueState continueState = ContinueState.CONTINUE_PENDING;

        private ContinueOutboundHandler(OutboundAccessImpl outboundAccess) {
            super(outboundAccess);
        }

        @Override
        void writeSome() {
            if (getPhase() == ResponsePhase.DONE) {
                return;
            }
            if (continueState == ContinueState.CONTINUE_PENDING) {
                continueState = ContinueState.CONTINUE_SENT;
                write(CONTINUE_11, true, false, false);
            }
            if (next != null && !removed) {
                retireContinue();
                outboundHandler = next;
                next.activate();
            }
        }

        @Override
        void markCloseAfterWrite() {
            if (next != null) {
                next.markCloseAfterWrite();
            }
        }

        @Override
        void discardOutbound() {
            retireContinue();
            if (next != null) {
                OutboundHandler discarded = next;
                next = null;
                discarded.discardOutbound();
            }
        }
    }

    /**
     * Handler that writes a {@link FullHttpResponse}.
     */
    private final class FullOutboundHandler extends OutboundHandler {
        @Nullable
        private FullHttpResponse message;

        FullOutboundHandler(OutboundAccessImpl outboundAccess, FullHttpResponse message) {
            super(outboundAccess);
            this.message = message;
        }

        @Override
        void markCloseAfterWrite() {
            if (getPhase() == ResponsePhase.QUEUED || getPhase() == ResponsePhase.HEADERS_PENDING) {
                addConnectionClose(Objects.requireNonNull(message));
            }
        }

        @Override
        void writeSome() {
            if (getPhase() == ResponsePhase.HEADERS_PENDING) {
                FullHttpResponse message = Objects.requireNonNull(this.message);
                this.message = null;
                finish(message);
            }
        }

        @Override
        void discardResources() {
            FullHttpResponse message = this.message;
            this.message = null;
            try {
                super.discardResources();
            } finally {
                if (message != null) {
                    message.release();
                }
            }
        }
    }

    /**
     * Handler that writes a {@link StreamedHttpResponse}.
     */
    @SuppressWarnings("checkstyle:MissingSwitchDefault") // List every phase explicitly.
    private final class StreamingOutboundHandler extends OutboundHandler implements BufferConsumer {
        private final EventLoopFlow flow = new EventLoopFlow(requiredCtx().channel().eventLoop());
        private final HttpResponse initialMessage;
        private BufferConsumer. @Nullable Upstream upstream;
        private PendingOutcome pendingOutcome = PendingOutcome.OPEN;
        @Nullable
        private Throwable pendingFailure;
        /**
         * Bytes received while queued, or reentrantly while writing headers and earlier bytes.
         */
        @Nullable
        private List<ReadBuffer> pendingData;
        private long incompleteWrittenBytes = 0;

        StreamingOutboundHandler(OutboundAccessImpl outboundAccess, HttpResponse initialMessage) {
            super(outboundAccess);
            if (initialMessage instanceof FullHttpResponse) {
                throw new IllegalArgumentException("Cannot have a full response as the initial message of a streaming response");
            }
            this.initialMessage = Objects.requireNonNull(initialMessage, "initialMessage");
        }

        @Override
        void markCloseAfterWrite() {
            if (getPhase() == ResponsePhase.QUEUED || getPhase() == ResponsePhase.HEADERS_PENDING) {
                addConnectionClose(initialMessage);
            }
        }

        /**
         * Commit header writing before calling out, and buffer reentrant signals until all
         * earlier bytes have been written. A queued failure must not send response headers.
         */
        private void drainPending(@Nullable ReadBuffer last) {
            assert requiredCtx().executor().inEventLoop();
            if (getPhase() != ResponsePhase.HEADERS_PENDING) {
                return;
            }
            if (pendingOutcome == PendingOutcome.FAILED) {
                error0(Objects.requireNonNull(pendingFailure));
                return;
            }
            transition(ResponsePhase.HEADERS_PENDING, ResponsePhase.WRITING_HEADERS);
            try {
                writePotentialEnd(initialMessage, false, false);
                while (getPhase() == ResponsePhase.WRITING_HEADERS && pendingOutcome != PendingOutcome.FAILED && pendingData != null) {
                    List<ReadBuffer> data = pendingData;
                    pendingData = null;
                    try {
                        for (ReadBuffer buf : data) {
                            if (getPhase() == ResponsePhase.WRITING_HEADERS && pendingOutcome != PendingOutcome.FAILED) {
                                if (buf == last && buf == data.getLast() && pendingData == null) {
                                    finish(new DefaultLastHttpContent(NettyReadBufferFactory.toByteBuf(buf)));
                                } else {
                                    writeContent(buf);
                                }
                            }
                        }
                    } finally {
                        for (ReadBuffer buf : data) {
                            buf.close();
                        }
                    }
                }
            } catch (Throwable t) {
                try {
                    discardOutbound();
                } catch (Throwable cleanupError) {
                    t.addSuppressed(cleanupError);
                } finally {
                    requiredCtx().close();
                }
                throw t;
            }
            if (getPhase() != ResponsePhase.WRITING_HEADERS) {
                return;
            }
            transition(ResponsePhase.WRITING_HEADERS, ResponsePhase.STREAMING);
            if (pendingOutcome == PendingOutcome.FAILED) {
                error0(Objects.requireNonNull(pendingFailure));
                return;
            }
        }

        @Override
        void writeSome() {
            if (getPhase() == ResponsePhase.HEADERS_PENDING) {
                drainPending(null);
                if (getPhase() == ResponsePhase.STREAMING) {
                    Objects.requireNonNull(upstream).start();
                }
            }
            if (getPhase() == ResponsePhase.STREAMING) {
                if (pendingOutcome == PendingOutcome.COMPLETE) {
                    complete0();
                } else if (requiredCtx().channel().isWritable()) {
                    long written = incompleteWrittenBytes;
                    if (written > 0) {
                        incompleteWrittenBytes = 0;
                        Objects.requireNonNull(upstream).onBytesConsumed(written);
                    }
                }
            }
        }

        @Override
        public void add(ReadBuffer buf) {
            if (flow.executeNow(() -> add0(buf))) {
                add0(buf);
            }
        }

        private void add0(ReadBuffer buf) {
            assert requiredCtx().executor().inEventLoop();
            switch (getPhase()) {
                case QUEUED, HEADERS_PENDING, WRITING_HEADERS -> {
                    enqueue(buf);
                    writeSome();
                }
                case STREAMING -> {
                    writeContent(buf);
                    if (requiredCtx().channel().isWritable()) {
                        writeSome();
                    }
                }
                case DONE -> buf.close();
            }
        }

        private void enqueue(ReadBuffer buf) {
            if (pendingData == null) {
                pendingData = new ArrayList<>(1);
            }
            pendingData.add(buf);
        }

        private void writeContent(ReadBuffer buf) {
            int n = buf.readable();
            writeCompressing(new DefaultHttpContent(NettyReadBufferFactory.toByteBuf(buf)), true, false);
            incompleteWrittenBytes += n;
        }

        private void releasePendingData() {
            List<ReadBuffer> data = pendingData;
            if (data != null) {
                pendingData = null;
                for (ReadBuffer buf : data) {
                    buf.close();
                }
            }
        }

        @Override
        public void addAndComplete(ReadBuffer buf) {
            if (flow.executeNow(() -> addAndComplete0(buf))) {
                addAndComplete0(buf);
            }
        }

        private void addAndComplete0(ReadBuffer buf) {
            assert requiredCtx().executor().inEventLoop();
            switch (getPhase()) {
                case QUEUED, HEADERS_PENDING, WRITING_HEADERS -> {
                    enqueue(buf);
                    // Preserve the final-content write when this signal commits the headers.
                    drainPending(buf);
                    complete0();
                }
                case STREAMING -> finish(new DefaultLastHttpContent(NettyReadBufferFactory.toByteBuf(buf)));
                case DONE -> buf.close();
            }
        }

        @Override
        public void error(Throwable t) {
            if (flow.executeNow(() -> error0(t))) {
                error0(t);
            }
        }

        private void error0(Throwable t) {
            assert requiredCtx().executor().inEventLoop();
            switch (getPhase()) {
                case QUEUED, WRITING_HEADERS -> {
                    // Do not cut off an earlier pipelined response, or interrupt header replay.
                    pendingOutcome = PendingOutcome.FAILED;
                    pendingFailure = t;
                }
                case HEADERS_PENDING, STREAMING -> {
                    if (LOG.isWarnEnabled()) {
                        if (getPhase() == ResponsePhase.STREAMING) {
                            LOG.warn("Reactive response received an error after some data has already been written. This error cannot be forwarded to the client.", t);
                        } else {
                            LOG.warn("Reactive response received an error before the response was written. This error cannot be forwarded to the client.", t);
                        }
                    }
                    discardOutbound();
                    requiredCtx().close();
                }
                case DONE -> {
                }
            }
        }

        @Override
        public void complete() {
            if (flow.executeNow(this::complete0)) {
                complete0();
            }
        }

        private void complete0() {
            assert requiredCtx().executor().inEventLoop();
            switch (getPhase()) {
                case QUEUED, HEADERS_PENDING, WRITING_HEADERS -> {
                    if (pendingOutcome == PendingOutcome.OPEN) {
                        pendingOutcome = PendingOutcome.COMPLETE;
                    }
                    drainPending(null);
                    if (getPhase() == ResponsePhase.STREAMING) {
                        finish(LastHttpContent.EMPTY_LAST_CONTENT);
                    }
                }
                case STREAMING -> finish(LastHttpContent.EMPTY_LAST_CONTENT);
                case DONE -> {
                }
            }
        }

        @Override
        void discardRefused() {
            // Serialize cleanup with body signals that may already be queued.
            EventLoop eventLoop = requiredCtx().channel().eventLoop();
            if (eventLoop.inEventLoop()) {
                super.discardRefused();
            } else {
                eventLoop.execute(this::discardRefused);
            }
        }

        @Override
        void discardResources() {
            try {
                super.discardResources();
            } finally {
                try {
                    releasePendingData();
                } finally {
                    Objects.requireNonNull(upstream).allowDiscard();
                }
            }
        }
    }

}
