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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.exceptions.MessageBodyException;
import io.micronaut.http.netty.body.NettyWriteContext;
import io.micronaut.http.netty.stream.DelegateStreamedHttpRequest;
import io.micronaut.http.netty.stream.EmptyHttpRequest;
import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.micronaut.http.server.netty.SmartHttpContentCompressor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.EventLoop;
import io.netty.channel.FileRegion;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedInput;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Supplier;

/**
 * Netty handler that handles incoming {@link HttpRequest}s and forwards them to a
 * {@link RequestHandler}.
 *
 * @author Jonas Konrad
 * @since 4.0.0
 */
@Internal
public final class PipeliningServerHandler extends ChannelInboundHandlerAdapter {
    public static final Supplier<AttributeKey<SmartHttpContentCompressor>> ZERO_COPY_PREDICATE =
        SupplierUtil.memoized(() -> AttributeKey.newInstance("zero-copy-predicate"));

    private static final int LENGTH_8K = 8192;
    private static final Logger LOG = LoggerFactory.getLogger(PipeliningServerHandler.class);

    private final RequestHandler requestHandler;

    // these three handlers can be reused and are cached here
    private final DroppingInboundHandler droppingInboundHandler = new DroppingInboundHandler();
    private final InboundHandler baseInboundHandler = new MessageInboundHandler();
    private final OptimisticBufferingInboundHandler optimisticBufferingInboundHandler = new OptimisticBufferingInboundHandler();

    /**
     * Current handler for inbound messages.
     */
    private InboundHandler inboundHandler = baseInboundHandler;

    /**
     * Queue of outbound messages that can't be written yet.
     */
    private final Queue<OutboundAccess> outboundQueue = new ArrayDeque<>(1);
    /**
     * Current outbound message, or {@code null} if no outbound message is waiting.
     */
    @Nullable
    private OutboundHandler outboundHandler = null;

    private ChannelHandlerContext ctx;
    /**
     * {@code true} iff we are in a read operation, before {@link #channelReadComplete}.
     */
    private boolean reading = false;
    /**
     * {@code true} iff we want to read more data.
     */
    private boolean moreRequested = false;
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

    public PipeliningServerHandler(RequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }

    public static boolean canHaveBody(HttpResponseStatus status) {
        // All 1xx (Informational), 204 (No Content), and 304 (Not Modified)
        // responses do not include a message body
        return !(status == HttpResponseStatus.CONTINUE || status == HttpResponseStatus.SWITCHING_PROTOCOLS ||
            status == HttpResponseStatus.PROCESSING || status == HttpResponseStatus.NO_CONTENT ||
            status == HttpResponseStatus.NOT_MODIFIED);
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
     * Set whether we need more input, i.e. another call to {@link #channelRead}. This is usally a
     * {@link ChannelHandlerContext#read()} call, but it's coalesced until
     * {@link #channelReadComplete}.
     *
     * @param needMore {@code true} iff we need more input
     */
    private void setNeedMore(boolean needMore) {
        boolean oldMoreRequested = moreRequested;
        moreRequested = needMore;
        if (!oldMoreRequested && !reading && needMore) {
            ctx.read();
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        removed = true;
        if (outboundHandler != null) {
            outboundHandler.discard();
        }
        for (OutboundAccess queued : outboundQueue) {
            if (queued.handler != null) {
                queued.handler.discard();
            }
        }
        outboundQueue.clear();
        requestHandler.removed();
    }

    @Override
    public void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) throws Exception {
        reading = true;
        inboundHandler.read(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        inboundHandler.readComplete();
        reading = false;
        if (flushPending) {
            ctx.flush();
            flushPending = false;
        }
        if (moreRequested) {
            ctx.read();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        inboundHandler.handleUpstreamError(cause);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        writeSome();
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
    private void write(Object message, boolean flush, boolean close) {
        if (close) {
            ctx.writeAndFlush(message).addListener(ChannelFutureListener.CLOSE);
        } else {
            if (flush) {
                // delay flush until readComplete if possible
                if (reading) {
                    ctx.write(message, ctx.voidPromise());
                    flushPending = true;
                } else {
                    ctx.writeAndFlush(message, ctx.voidPromise());
                }
            } else {
                ctx.write(message, ctx.voidPromise());
            }
        }
    }

    /**
     * Write some data if possible.
     */
    private void writeSome() {
        if (writing) {
            // already inside writeSome
            return;
        }
        writing = true;
        try {
            while (ctx.channel().isWritable()) {
                // if we have no outboundHandler, check whether the first queued response is ready
                if (outboundHandler == null) {
                    OutboundAccess next = outboundQueue.peek();
                    if (next != null && next.handler != null) {
                        outboundQueue.poll();
                        outboundHandler = next.handler;
                    } else {
                        return;
                    }
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

    /**
     * An inbound handler is responsible for all incoming messages.
     */
    private abstract static class InboundHandler {
        /**
         * @see #channelRead
         */
        abstract void read(Object message);

        /**
         * @see #exceptionCaught
         */
        abstract void handleUpstreamError(Throwable cause);

        /**
         * @see #channelReadComplete
         */
        void readComplete() {
        }
    }

    /**
     * Wrapper class for a netty response with a special body type, like
     * {@link HttpChunkedInput} or
     * {@link FileRegion}.
     *
     * @param response The response
     * @param body     The body, or {@code null} if there is no body
     * @param needLast Whether to finish the response with a
     *                 {@link LastHttpContent}
     */
    private record CustomResponse(HttpResponse response, @Nullable Object body, boolean needLast) {
        CustomResponse {
            if (response instanceof FullHttpResponse) {
                throw new IllegalArgumentException("Response must not be a FullHttpResponse to send a special body");
            }
        }
    }

    /**
     * Base {@link InboundHandler} that handles {@link HttpRequest}s and then determines how to
     * deal with the body.
     */
    private final class MessageInboundHandler extends InboundHandler {
        @Override
        void read(Object message) {
            HttpRequest request = (HttpRequest) message;
            OutboundAccess outboundAccess = new OutboundAccess();
            outboundQueue.add(outboundAccess);
            if (request instanceof FullHttpRequest full) {
                requestHandler.accept(ctx, full, outboundAccess);
            } else if (!hasBody(request)) {
                inboundHandler = droppingInboundHandler;
                if (message instanceof HttpContent) {
                    inboundHandler.read(message);
                }
                requestHandler.accept(ctx, new EmptyHttpRequest(request), outboundAccess);
            } else {
                optimisticBufferingInboundHandler.init(request, outboundAccess);
                inboundHandler = optimisticBufferingInboundHandler;
            }
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
        private HttpRequest request;
        private OutboundAccess outboundAccess;
        private final List<HttpContent> buffer = new ArrayList<>();

        void init(HttpRequest request, OutboundAccess outboundAccess) {
            assert buffer.isEmpty();
            assert !(request instanceof HttpContent);
            this.request = request;
            this.outboundAccess = outboundAccess;
        }

        @Override
        void read(Object message) {
            HttpContent content = (HttpContent) message;
            if (content.content().isReadable()) {
                buffer.add(content);
            } else {
                content.release();
            }
            if (message instanceof LastHttpContent last) {
                // we got the full message before readComplete
                ByteBuf fullBody;
                if (buffer.size() == 0) {
                    fullBody = Unpooled.EMPTY_BUFFER;
                } else if (buffer.size() == 1) {
                    fullBody = buffer.get(0).content();
                } else {
                    CompositeByteBuf composite = ctx.alloc().compositeBuffer();
                    for (HttpContent c : buffer) {
                        composite.addComponent(true, c.content());
                    }
                    fullBody = composite;
                }
                buffer.clear();
                FullHttpRequest fullRequest = new DefaultFullHttpRequest(
                    request.protocolVersion(),
                    request.method(),
                    request.uri(),
                    fullBody,
                    request.headers(),
                    last.trailingHeaders()
                );
                fullRequest.setDecoderResult(request.decoderResult());
                request = null;
                OutboundAccess outboundAccess = this.outboundAccess;
                this.outboundAccess = null;
                requestHandler.accept(ctx, fullRequest, outboundAccess);

                inboundHandler = baseInboundHandler;
            }
        }

        @Override
        void readComplete() {
            devolveToStreaming();
            inboundHandler.readComplete();
        }

        @Override
        void handleUpstreamError(Throwable cause) {
            devolveToStreaming();
            inboundHandler.handleUpstreamError(cause);
        }

        private void devolveToStreaming() {
            StreamingInboundHandler streamingInboundHandler = new StreamingInboundHandler();
            for (HttpContent content : buffer) {
                streamingInboundHandler.read(content);
            }
            buffer.clear();
            HttpRequest request = this.request;
            OutboundAccess outboundAccess = this.outboundAccess;
            this.request = null;
            this.outboundAccess = null;

            inboundHandler = streamingInboundHandler;
            Flux<HttpContent> flux = streamingInboundHandler.flux();
            if (HttpUtil.is100ContinueExpected(request)) {
                flux = flux.doOnSubscribe(s -> outboundAccess.writeContinue());
            }
            requestHandler.accept(ctx, new DelegateStreamedHttpRequest(request, flux) {
                @Override
                public void closeIfNoSubscriber() {
                    streamingInboundHandler.closeIfNoSubscriber();
                }
            }, outboundAccess);
        }
    }

    /**
     * Handler that exposes incoming content as a {@link Flux}.
     */
    private final class StreamingInboundHandler extends InboundHandler {
        private final Queue<HttpContent> queue = Queues.<HttpContent>unbounded().get();
        private final Sinks.Many<HttpContent> sink = Sinks.many().unicast().onBackpressureBuffer(queue);
        private long requested = 0;

        @Override
        void read(Object message) {
            requested--;
            HttpContent content = (HttpContent) message;
            if (sink.tryEmitNext(content.touch()) != Sinks.EmitResult.OK) {
                content.release();
            }
            if (message instanceof LastHttpContent) {
                sink.tryEmitComplete();
                inboundHandler = baseInboundHandler;
            }
            setNeedMore(requested > 0);
        }

        @Override
        void handleUpstreamError(Throwable cause) {
            releaseQueue();
            if (sink.tryEmitError(cause) != Sinks.EmitResult.OK) {
                requestHandler.handleUnboundError(cause);
            }
        }

        private void request(long n) {
            EventLoop eventLoop = ctx.channel().eventLoop();
            if (!eventLoop.inEventLoop()) {
                eventLoop.execute(() -> request(n));
                return;
            }

            long newRequested = requested + n;
            if (newRequested < requested) {
                // overflow
                newRequested = Long.MAX_VALUE;
            }
            requested = newRequested;
            setNeedMore(newRequested > 0);
        }

        Flux<HttpContent> flux() {
            return sink.asFlux()
                .doOnRequest(this::request)
                .doOnCancel(this::releaseQueue);
        }

        void closeIfNoSubscriber() {
            if (sink.currentSubscriberCount() == 0) {
                releaseQueue();
                if (inboundHandler == this) {
                    inboundHandler = droppingInboundHandler;
                }
            }
        }

        private void releaseQueue() {
            while (true) {
                HttpContent c = queue.poll();
                if (c == null) {
                    break;
                }
                c.release();
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
            if (message instanceof LastHttpContent) {
                inboundHandler = baseInboundHandler;
            }
        }

        @Override
        void handleUpstreamError(Throwable cause) {
            requestHandler.handleUnboundError(cause);
        }
    }

    /**
     * Class that allows writing the response for the request this object is associated with.
     */
    public final class OutboundAccess implements NettyWriteContext {
        /**
         * The handler that will perform the actual write operation.
         */
        private OutboundHandler handler;
        private Object attachment = null;
        private boolean closeAfterWrite = false;

        private OutboundAccess() {
        }

        @Override
        public ByteBufAllocator alloc() {
            return ctx.alloc();
        }

        /**
         * Set an attachment that is passed to {@link RequestHandler#responseWritten}. Defaults to
         * {@code null}.
         *
         * @param attachment The attachment to forward
         */
        public void attachment(Object attachment) {
            this.attachment = attachment;
        }

        /**
         * Mark this channel to be closed after this response has been written.
         */
        public void closeAfterWrite() {
            closeAfterWrite = true;
        }

        private void preprocess(HttpResponse message) {
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
        }

        /**
         * Write a 100 CONTINUE response.
         */
        private void writeContinue() {
            if (handler == null) {
                write(new ContinueOutboundHandler());
            }
        }

        /**
         * Write a response using the given outbound handler, when ready.
         */
        private void write(OutboundHandler handler) {
            // technically handler should be volatile for this check, but this is only for sanity anyway
            if (this.handler != null && !(this.handler instanceof ContinueOutboundHandler)) {
                throw new IllegalStateException("Only one response per request");
            }

            EventLoop eventLoop = ctx.channel().eventLoop();
            if (!eventLoop.inEventLoop()) {
                eventLoop.execute(() -> write(handler));
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
        public void writeFull(FullHttpResponse response, boolean headResponse) {
            response.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
            if (canHaveBody(response.status())) {
                if (!headResponse) {
                    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
                }
            } else {
                response.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
            }
            preprocess(response);
            write(new FullOutboundHandler(this, response));
        }

        @Override
        public void writeStreamed(HttpResponse response, Publisher<HttpContent> content) {
            response.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
            if (canHaveBody(response.status())) {
                response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            } else {
                response.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
            }
            preprocess(response);
            content.subscribe(new StreamingOutboundHandler(this, response));
        }

        /**
         * Write a response with a special body
         * ({@link io.netty.handler.codec.http.HttpChunkedInput},
         * {@link io.micronaut.http.server.types.files.SystemFile}).
         *
         * @param response The response to write
         */
        private void writeStreamed(CustomResponse response) {
            preprocess(response.response());
            write(new ChunkedOutboundHandler(this, response));
        }

        @Override
        public void writeChunked(HttpResponse response, HttpChunkedInput chunkedInput) {
            writeStreamed(new CustomResponse(response, chunkedInput, false));
        }

        @Override
        public void writeFile(HttpResponse response, RandomAccessFile randomAccessFile, long position, long contentLength) {
            SmartHttpContentCompressor predicate = ctx.channel().attr(ZERO_COPY_PREDICATE.get()).get();
            if (predicate != null && predicate.shouldSkip(response)) {
                // SSL not enabled - can use zero-copy file transfer.
                writeStreamed(new CustomResponse(response, new TrackedDefaultFileRegion(randomAccessFile.getChannel(), position, contentLength), true));
            } else {
                // SSL enabled - cannot use zero-copy file transfer.
                try {
                    // HttpChunkedInput will write the end marker (LastHttpContent) for us.
                    final HttpChunkedInput chunkedInput = new HttpChunkedInput(new TrackedChunkedFile(randomAccessFile, position, contentLength, LENGTH_8K));
                    writeStreamed(new CustomResponse(response, chunkedInput, false));
                } catch (IOException e) {
                    throw new MessageBodyException("Could not read file", e);
                }
            }
        }
    }

    private abstract static class OutboundHandler {
        /**
         * {@link OutboundAccess} that created this handler, for metadata access.
         */
        final OutboundAccess outboundAccess;

        private OutboundHandler(OutboundAccess outboundAccess) {
            this.outboundAccess = outboundAccess;
        }

        /**
         * Write some data to the channel.
         */
        abstract void writeSome();

        /**
         * Discard the remaining data.
         */
        abstract void discard();
    }

    /**
     * Handler that writes a 100 CONTINUE response and then proceeds with the {@link #next} handler.
     */
    private final class ContinueOutboundHandler extends OutboundHandler {
        private static final FullHttpResponse CONTINUE =
            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER);

        boolean written = false;
        OutboundHandler next;

        private ContinueOutboundHandler() {
            super(null);
        }

        @Override
        void writeSome() {
            if (!written) {
                write(CONTINUE, true, false);
                written = true;
            }
            if (next != null) {
                outboundHandler = next;
            }
        }

        @Override
        void discard() {
            if (next != null) {
                next.discard();
                next = null;
            }
        }
    }

    /**
     * Handler that writes a {@link FullHttpResponse}.
     */
    private final class FullOutboundHandler extends OutboundHandler {
        private final FullHttpResponse message;

        FullOutboundHandler(OutboundAccess outboundAccess, FullHttpResponse message) {
            super(outboundAccess);
            this.message = message;
        }

        @Override
        void writeSome() {
            write(message, true, outboundAccess.closeAfterWrite);
            outboundHandler = null;
            requestHandler.responseWritten(outboundAccess.attachment);
            PipeliningServerHandler.this.writeSome();
        }

        @Override
        void discard() {
            message.release();
            outboundHandler = null;
        }
    }

    /**
     * Handler that writes a {@link StreamedHttpResponse}.
     */
    private final class StreamingOutboundHandler extends OutboundHandler implements Subscriber<HttpContent> {
        private final OutboundAccess outboundAccess;
        private HttpResponse initialMessage;
        private Subscription subscription;
        private boolean writtenLast = false;

        StreamingOutboundHandler(OutboundAccess outboundAccess, HttpResponse initialMessage) {
            super(outboundAccess);
            if (initialMessage instanceof FullHttpResponse) {
                throw new IllegalArgumentException("Cannot have a full response as the initial message of a streaming response");
            }
            this.outboundAccess = outboundAccess;
            this.initialMessage = Objects.requireNonNull(initialMessage, "initialMessage");
        }

        @Override
        void writeSome() {
            if (initialMessage != null) {
                write(initialMessage, false, false);
                initialMessage = null;
            }
            subscription.request(1);
        }

        @Override
        public void onSubscribe(Subscription s) {
            subscription = s;
            // delay access.write call until the subscription is available, so that writeSome is
            // only called then
            outboundAccess.write(this);
        }

        @Override
        public void onNext(HttpContent httpContent) {
            EventLoop eventLoop = ctx.channel().eventLoop();
            if (!eventLoop.inEventLoop()) {
                eventLoop.execute(() -> onNext(httpContent));
                return;
            }

            if (outboundHandler != this) {
                throw new IllegalStateException("onNext before request?");
            }

            if (writtenLast) {
                throw new IllegalStateException("Already written a LastHttpContent");
            }

            if (!removed) {
                if (httpContent instanceof LastHttpContent) {
                    writtenLast = true;
                }
                write(httpContent, true, false);
                if (ctx.channel().isWritable()) {
                    subscription.request(1);
                }
            } else {
                httpContent.release();
            }
        }

        @Override
        public void onError(Throwable t) {
            EventLoop eventLoop = ctx.channel().eventLoop();
            if (!eventLoop.inEventLoop()) {
                eventLoop.execute(() -> onError(t));
                return;
            }

            if (!removed) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Reactive response received an error after some data has already been written. This error cannot be forwarded to the client.", t);
                }
                ctx.close();

                requestHandler.responseWritten(outboundAccess.attachment);
            }
        }

        @Override
        public void onComplete() {
            EventLoop eventLoop = ctx.channel().eventLoop();
            if (!eventLoop.inEventLoop()) {
                eventLoop.execute(this::onComplete);
                return;
            }

            if (outboundHandler != this) {
                throw new IllegalStateException("onComplete before request?");
            }

            outboundHandler = null;
            if (!removed) {
                if (initialMessage != null) {
                    write(initialMessage, false, false);
                    initialMessage = null;
                }

                if (!writtenLast) {
                    write(LastHttpContent.EMPTY_LAST_CONTENT, true, outboundAccess.closeAfterWrite);
                }
                requestHandler.responseWritten(outboundAccess.attachment);
                PipeliningServerHandler.this.writeSome();
            }
        }

        @Override
        void discard() {
            // this is safe because:
            // - onComplete/onError cannot have been called yet, because otherwise outboundHandler
            //   would be null and discard couldn't have been called
            // - while cancel() may trigger onComplete/onError, `removed` is true at this point, so
            //   they won't call responseWritten in turn
            requestHandler.responseWritten(outboundAccess.attachment);
            subscription.cancel();
            outboundHandler = null;
        }
    }

    /**
     * Handler that writes a files etc.
     */
    private final class ChunkedOutboundHandler extends OutboundHandler {
        private final CustomResponse message;

        ChunkedOutboundHandler(OutboundAccess outboundAccess, CustomResponse message) {
            super(outboundAccess);
            this.message = message;
        }

        @Override
        void writeSome() {
            boolean responseIsLast = message.body() == null && !message.needLast();
            write(message.response(), responseIsLast, responseIsLast && outboundAccess.closeAfterWrite);
            if (message.body() != null) {
                boolean bodyIsLast = !message.needLast();
                write(message.body(), bodyIsLast, bodyIsLast && outboundAccess.closeAfterWrite);
            }
            if (message.needLast()) {
                write(LastHttpContent.EMPTY_LAST_CONTENT, true, outboundAccess.closeAfterWrite);
            }
            outboundHandler = null;
            requestHandler.responseWritten(outboundAccess.attachment);
            PipeliningServerHandler.this.writeSome();
        }

        @Override
        void discard() {
            ReferenceCountUtil.release(message.response());
            if (message.body() instanceof ChunkedInput<?> ci) {
                try {
                    ci.close();
                } catch (Exception e) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("Failed to close ChunkedInput", e);
                    }
                }
            } else if (message.body() instanceof FileRegion fr) {
                fr.release();
            }
            outboundHandler = null;
        }
    }

    private static class TrackedDefaultFileRegion extends DefaultFileRegion {
        //to avoid initializing Netty at build time
        private static final Supplier<ResourceLeakDetector<TrackedDefaultFileRegion>> LEAK_DETECTOR = SupplierUtil.memoized(() ->
            ResourceLeakDetectorFactory.instance().newResourceLeakDetector(TrackedDefaultFileRegion.class));

        private final ResourceLeakTracker<TrackedDefaultFileRegion> tracker;

        public TrackedDefaultFileRegion(FileChannel fileChannel, long position, long count) {
            super(fileChannel, position, count);
            this.tracker = LEAK_DETECTOR.get().track(this);
        }

        @Override
        protected void deallocate() {
            super.deallocate();
            if (tracker != null) {
                tracker.close(this);
            }
        }
    }

    private static class TrackedChunkedFile extends ChunkedFile {
        //to avoid initializing Netty at build time
        private static final Supplier<ResourceLeakDetector<TrackedChunkedFile>> LEAK_DETECTOR = SupplierUtil.memoized(() ->
            ResourceLeakDetectorFactory.instance().newResourceLeakDetector(TrackedChunkedFile.class));

        private final ResourceLeakTracker<TrackedChunkedFile> tracker;

        public TrackedChunkedFile(RandomAccessFile file, long offset, long length, int chunkSize) throws IOException {
            super(file, offset, length, chunkSize);
            this.tracker = LEAK_DETECTOR.get().track(this);
        }

        @Override
        public void close() throws Exception {
            super.close();
            if (tracker != null) {
                tracker.close(this);
            }
        }
    }
}
