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
import io.micronaut.http.netty.EventLoopFlow;
import io.micronaut.http.netty.body.NettyWriteContext;
import io.micronaut.http.netty.reactive.HotObservable;
import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.micronaut.http.server.netty.HttpCompressionStrategy;
import io.micronaut.http.server.netty.body.ByteBody;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.compression.BrotliDecoder;
import io.netty.handler.codec.compression.DecompressionException;
import io.netty.handler.codec.compression.SnappyFrameDecoder;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
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
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Netty handler that handles incoming {@link HttpRequest}s and forwards them to a
 * {@link RequestHandler}.
 *
 * @author Jonas Konrad
 * @since 4.0.0
 */
@Internal
public final class PipeliningServerHandler extends ChannelInboundHandlerAdapter {
    private static final int LENGTH_8K = 8192;
    private static final Logger LOG = LoggerFactory.getLogger(PipeliningServerHandler.class);

    private final RequestHandler requestHandler;

    // these three handlers can be reused and are cached here
    private final DroppingInboundHandler droppingInboundHandler = new DroppingInboundHandler();
    private final InboundHandler baseInboundHandler = new MessageInboundHandler();
    private final OptimisticBufferingInboundHandler optimisticBufferingInboundHandler = new OptimisticBufferingInboundHandler();

    private Compressor compressor;

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

    public PipeliningServerHandler(RequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }

    public void setCompressionStrategy(HttpCompressionStrategy compressionStrategy) {
        this.compressor = new Compressor(compressionStrategy);
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
     * Call {@code ctx.read()} if necessary.
     */
    private void refreshNeedMore() {
        // if readCalled is true, ctx.read() is already called and we haven't seen the associated readComplete yet.

        // needMore is false if there is downstream backpressure.

        // requestHandler itself (i.e. non-streaming request processing) does not have
        // backpressure. For this, check whether there is a request that has been fully read but
        // has no response yet. If there is, apply backpressure.
        if (!readCalled && outboundQueue.size() <= 1 && inboundHandler.needMore()) {
            readCalled = true;
            ctx.read();
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
        // we take control of reading now.
        ctx.channel().config().setAutoRead(false);
        refreshNeedMore();
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
        // only unset readCalled now. This ensures no read call is done before channelReadComplete
        readCalled = false;
        if (flushPending) {
            ctx.flush();
            flushPending = false;
        }
        refreshNeedMore();
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
                        refreshNeedMore();
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
    private abstract class InboundHandler {
        /**
         * @return {@code true} iff this handler can process more data. This is usually {@code true},
         * except for streaming requests when there is downstream backpressure.
         */
        boolean needMore() {
            return true;
        }

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
     * Base {@link InboundHandler} that handles {@link HttpRequest}s and then determines how to
     * deal with the body.
     */
    private final class MessageInboundHandler extends InboundHandler {
        @Override
        void read(Object message) {
            HttpRequest request = (HttpRequest) message;
            OutboundAccess outboundAccess = new OutboundAccess(request);
            outboundQueue.add(outboundAccess);

            HttpHeaders headers = request.headers();
            String contentEncoding = getContentEncoding(headers);
            EmbeddedChannel decompressionChannel;
            if (contentEncoding == null) {
                decompressionChannel = null;
            } else if (HttpHeaderValues.GZIP.contentEqualsIgnoreCase(contentEncoding) ||
                HttpHeaderValues.X_GZIP.contentEqualsIgnoreCase(contentEncoding)) {
                decompressionChannel = new EmbeddedChannel(ctx.channel().id(), ctx.channel().metadata().hasDisconnect(),
                    ctx.channel().config(), ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP));
            } else if (HttpHeaderValues.DEFLATE.contentEqualsIgnoreCase(contentEncoding) ||
                HttpHeaderValues.X_DEFLATE.contentEqualsIgnoreCase(contentEncoding)) {
                decompressionChannel = new EmbeddedChannel(ctx.channel().id(), ctx.channel().metadata().hasDisconnect(),
                    ctx.channel().config(), ZlibCodecFactory.newZlibDecoder(ZlibWrapper.ZLIB_OR_NONE));
            } else if (Brotli.isAvailable() && HttpHeaderValues.BR.contentEqualsIgnoreCase(contentEncoding)) {
                decompressionChannel = new EmbeddedChannel(ctx.channel().id(), ctx.channel().metadata().hasDisconnect(),
                    ctx.channel().config(), new BrotliDecoder());
            } else if (HttpHeaderValues.SNAPPY.contentEqualsIgnoreCase(contentEncoding)) {
                decompressionChannel = new EmbeddedChannel(ctx.channel().id(), ctx.channel().metadata().hasDisconnect(),
                    ctx.channel().config(), new SnappyFrameDecoder());
            } else {
                decompressionChannel = null;
            }
            if (decompressionChannel != null) {
                headers.remove(HttpHeaderNames.CONTENT_LENGTH);
                headers.remove(HttpHeaderNames.CONTENT_ENCODING);
                headers.add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            }

            boolean full = request instanceof FullHttpRequest;
            if (full && decompressionChannel == null) {
                requestHandler.accept(ctx, request, ByteBody.of(((FullHttpRequest) request).content()), outboundAccess);
            } else if (!hasBody(request)) {
                inboundHandler = droppingInboundHandler;
                if (message instanceof HttpContent) {
                    inboundHandler.read(message);
                }
                if (decompressionChannel != null) {
                    decompressionChannel.finish();
                }
                requestHandler.accept(ctx, request, ByteBody.empty(), outboundAccess);
            } else {
                optimisticBufferingInboundHandler.init(request, outboundAccess);
                if (decompressionChannel == null) {
                    inboundHandler = optimisticBufferingInboundHandler;
                } else {
                    inboundHandler = new DecompressingInboundHandler(decompressionChannel, optimisticBufferingInboundHandler);
                }
                if (full) {
                    inboundHandler.read(new DefaultLastHttpContent(((FullHttpRequest) request).content()));
                }
            }
        }

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
                } else {
                    contentEncoding = null;
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
        private HttpRequest request;
        private OutboundAccess outboundAccess;
        private final List<HttpContent> buffer = new ArrayList<>();

        void init(HttpRequest request, OutboundAccess outboundAccess) {
            assert buffer.isEmpty();
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
                HttpRequest request = this.request;
                this.request = null;
                OutboundAccess outboundAccess = this.outboundAccess;
                this.outboundAccess = null;
                requestHandler.accept(ctx, request, ByteBody.of(fullBody), outboundAccess);

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

            if (inboundHandler == this) {
                inboundHandler = streamingInboundHandler;
            } else {
                ((DecompressingInboundHandler) inboundHandler).delegate = streamingInboundHandler;
            }
            Flux<HttpContent> flux;
            if (HttpUtil.is100ContinueExpected(request)) {
                flux = streamingInboundHandler.flux().doOnSubscribe(s -> outboundAccess.writeContinue());
            } else {
                flux = streamingInboundHandler.flux();
            }
            requestHandler.accept(ctx, request, ByteBody.of(new HotObservable<>() {
                @Override
                public void closeIfNoSubscriber() {
                    streamingInboundHandler.closeIfNoSubscriber();
                }

                @Override
                public void subscribe(Subscriber<? super HttpContent> s) {
                    flux.subscribe(s);
                }
            }, HttpUtil.getContentLength(request, -1L)), outboundAccess);
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
        }

        @Override
        void handleUpstreamError(Throwable cause) {
            releaseQueue();
            if (sink.tryEmitError(cause) != Sinks.EmitResult.OK) {
                requestHandler.handleUnboundError(cause);
            }
        }

        @Override
        boolean needMore() {
            return requested > 0;
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
            refreshNeedMore();
        }

        Flux<HttpContent> flux() {
            return sink.asFlux()
                .doOnRequest(this::request)
                .doOnCancel(this::cancel);
        }

        void closeIfNoSubscriber() {
            EventLoop eventLoop = ctx.channel().eventLoop();
            if (!eventLoop.inEventLoop()) {
                eventLoop.execute(this::closeIfNoSubscriber);
                return;
            }

            if (sink.currentSubscriberCount() == 0) {
                cancelImpl();
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

        private void cancel() {
            EventLoop eventLoop = ctx.channel().eventLoop();
            if (!eventLoop.inEventLoop()) {
                eventLoop.execute(this::cancel);
                return;
            }

            cancelImpl();
        }

        private void cancelImpl() {
            if (inboundHandler == this) {
                inboundHandler = droppingInboundHandler;
                refreshNeedMore();
            } else if (inboundHandler instanceof DecompressingInboundHandler dec && dec.delegate == this) {
                dec.dispose();
                inboundHandler = droppingInboundHandler;
                refreshNeedMore();
            }
            releaseQueue();
        }
    }

    private class DecompressingInboundHandler extends InboundHandler {
        private final EmbeddedChannel channel;
        private InboundHandler delegate;

        public DecompressingInboundHandler(EmbeddedChannel channel, InboundHandler delegate) {
            this.channel = channel;
            this.delegate = delegate;
        }

        @Override
        void read(Object message) {
            ByteBuf compressed = ((HttpContent) message).content();
            if (!compressed.isReadable()) {
                delegate.read(message);
                return;
            }

            channel.writeInbound(compressed);
            boolean last = message instanceof LastHttpContent;
            if (last) {
                channel.finish();
            }

            while (true) {
                ByteBuf decompressed = channel.readInbound();
                if (decompressed == null) {
                    break;
                }
                if (!decompressed.isReadable()) {
                    decompressed.release();
                    continue;
                }
                delegate.read(new DefaultHttpContent(decompressed));
            }

            if (last) {
                delegate.read(LastHttpContent.EMPTY_LAST_CONTENT);
            }
        }

        void dispose() {
            try {
                channel.finishAndReleaseAll();
            } catch (DecompressionException ignored) {
            }
        }

        @Override
        void readComplete() {
            delegate.readComplete();
        }

        @Override
        void handleUpstreamError(Throwable cause) {
            delegate.handleUpstreamError(cause);
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
         * The request that caused this response. This is used for compression decisions.
         */
        private final HttpRequest request;
        /**
         * The handler that will perform the actual write operation.
         */
        private OutboundHandler handler;
        private Object attachment = null;
        private boolean closeAfterWrite = false;

        private OutboundAccess(HttpRequest request) {
            this.request = request;
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
        }

        /**
         * Write a 100 CONTINUE response.
         */
        private void writeContinue() {
            if (handler == null) {
                write(new ContinueOutboundHandler(this));
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
            FullOutboundHandler oh = new FullOutboundHandler(this, response);
            prepareCompression(response, oh);
            write(oh);
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
            StreamingOutboundHandler oh = new StreamingOutboundHandler(this, response);
            prepareCompression(response, oh);
            content.subscribe(oh);
        }

        @Override
        public void writeStream(HttpResponse response, InputStream stream, ExecutorService executorService) {
            preprocess(response);
            BlockingOutboundHandler oh = new BlockingOutboundHandler(this, response, stream, executorService);
            prepareCompression(response, oh);
            write(oh);
        }

        private void prepareCompression(HttpResponse response, OutboundHandler outboundHandler) {
            if (compressor == null) {
                return;
            }
            ChannelHandler compressionHandler = compressor.prepare(request, response);
            if (compressionHandler != null) {
                // if content-length and transfer-encoding are unset, we will close anyway.
                // if this is a full response, there's special handling below in OutboundHandler
                if (!(response instanceof FullHttpResponse) && response.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
                    response.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
                    response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                }
                outboundHandler.compressionChannel = new EmbeddedChannel(
                    ctx.channel().id(),
                    ctx.channel().metadata().hasDisconnect(),
                    ctx.channel().config(),
                    compressionHandler
                );
            }
        }
    }

    private abstract class OutboundHandler {
        /**
         * {@link OutboundAccess} that created this handler, for metadata access.
         */
        final OutboundAccess outboundAccess;

        EmbeddedChannel compressionChannel;

        private OutboundHandler(OutboundAccess outboundAccess) {
            this.outboundAccess = outboundAccess;
        }

        protected final void writeCompressing(HttpContent content, @SuppressWarnings("SameParameterValue") boolean flush, boolean close) {
            if (this.compressionChannel == null) {
                write(content, flush, close);
            } else {
                // slow path
                writeCompressing0(content, flush, close);
            }
        }

        private void writeCompressing0(HttpContent content, boolean flush, boolean close) {
            EmbeddedChannel compressionChannel = this.compressionChannel;
            if (content.content().isReadable()) {
                compressionChannel.writeOutbound(content.content());
            } else {
                content.content().release();
            }
            boolean last = content instanceof LastHttpContent;
            if (last) {
                compressionChannel.finish();
                this.compressionChannel = null;
            }
            if (content instanceof HttpResponse hr) {
                assert last;

                // fix content-length if necessary
                if (hr.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
                    long newContentLength = 0;
                    for (Object outboundMessage : compressionChannel.outboundMessages()) {
                        newContentLength += ((ByteBuf) outboundMessage).readableBytes();
                    }
                    hr.headers().set(HttpHeaderNames.CONTENT_LENGTH, newContentLength);
                }

                // this can happen in FullHttpResponse, just send the full body.
                write(new DefaultHttpResponse(hr.protocolVersion(), hr.status(), hr.headers()), false, false);
            }
            // this is a bit awkward. we go over all the compressed data, *except the last buffer*, and forward them with
            // flush=false and close=false. only for the last buffer we use those flags.
            ByteBuf nextToSend = null;
            while (true) {
                ByteBuf buf = compressionChannel.readOutbound();
                if (buf == null) {
                    break;
                }
                if (nextToSend != null) {
                    write(new DefaultHttpContent(nextToSend), false, false);
                }
                nextToSend = buf;
            }
            // send the last buffer with the flags.
            if (nextToSend == null) {
                if (last) {
                    HttpHeaders trailingHeaders = ((LastHttpContent) content).trailingHeaders();
                    write(trailingHeaders.isEmpty() ? LastHttpContent.EMPTY_LAST_CONTENT : new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER, trailingHeaders), flush, close);
                } else if (flush || close) {
                    // not sure if this can actually happen, but we need to forward a flush/close
                    write(new DefaultHttpContent(Unpooled.EMPTY_BUFFER), flush, close);
                } // else just don't send anything
            } else {
                if (last) {
                    write(new DefaultLastHttpContent(nextToSend, ((LastHttpContent) content).trailingHeaders()), flush, close);
                } else {
                    write(new DefaultHttpContent(nextToSend), flush, close);
                }
            }
        }

        /**
         * Write some data to the channel.
         */
        abstract void writeSome();

        /**
         * Discard the remaining data.
         */
        void discard() {
            EmbeddedChannel compressionChannel = this.compressionChannel;
            if (compressionChannel != null) {
                try {
                    compressionChannel.finishAndReleaseAll();
                } catch (DecompressionException ignored) {
                }
                this.compressionChannel = null;
            }
        }
    }

    /**
     * Handler that writes a 100 CONTINUE response and then proceeds with the {@link #next} handler.
     */
    private final class ContinueOutboundHandler extends OutboundHandler {
        private static final FullHttpResponse CONTINUE_11 =
            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER);
        private static final FullHttpResponse CONTINUE_10 =
            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER);

        boolean written = false;
        OutboundHandler next;

        private ContinueOutboundHandler(OutboundAccess outboundAccess) {
            super(outboundAccess);
        }

        @Override
        void writeSome() {
            if (!written) {
                write(outboundAccess.request.protocolVersion().equals(HttpVersion.HTTP_1_0) ? CONTINUE_10 : CONTINUE_11, true, false);
                written = true;
            }
            if (next != null) {
                outboundHandler = next;
            }
        }

        @Override
        void discard() {
            super.discard();
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
            writeCompressing(message, true, outboundAccess.closeAfterWrite);
            outboundHandler = null;
            requestHandler.responseWritten(outboundAccess.attachment);
            PipeliningServerHandler.this.writeSome();
        }

        @Override
        void discard() {
            super.discard();
            outboundHandler = null;
            // pretend we wrote to clean up resources
            requestHandler.responseWritten(outboundAccess.attachment);
            message.release();
        }
    }

    /**
     * Handler that writes a {@link StreamedHttpResponse}.
     */
    private final class StreamingOutboundHandler extends OutboundHandler implements Subscriber<HttpContent> {
        private final EventLoopFlow flow = new EventLoopFlow(ctx.channel().eventLoop());
        private final OutboundAccess outboundAccess;
        private HttpResponse initialMessage;
        private Subscription subscription;
        private boolean earlyComplete = false;
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
            if (earlyComplete) {
                // onComplete has been called before the first writeSome. Trigger onComplete
                // handling again.
                onComplete();
            } else {
                subscription.request(1);
            }
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
            if (flow.executeNow(() -> onNext0(httpContent))) {
                onNext0(httpContent);
            }
        }

        private void onNext0(HttpContent httpContent) {
            if (outboundHandler != this) {
                throw new IllegalStateException("onNext before request?");
            }

            if (writtenLast) {
                throw new IllegalStateException("Already written a LastHttpContent");
            }

            if (!removed) {
                boolean last = httpContent instanceof LastHttpContent;
                if (last) {
                    writtenLast = true;
                }
                writeCompressing(httpContent, true, last && outboundAccess.closeAfterWrite);
                if (ctx.channel().isWritable()) {
                    subscription.request(1);
                }
            } else {
                httpContent.release();
            }
        }

        @Override
        public void onError(Throwable t) {
            if (flow.executeNow(() -> onError0(t))) {
                onError0(t);
            }
        }

        private void onError0(Throwable t) {
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
            if (flow.executeNow(this::onComplete0)) {
                onComplete0();
            }
        }

        private void onComplete0() {
            if (outboundHandler != this) {
                // onComplete can be called immediately after onSubscribe, before request.
                earlyComplete = true;
                return;
            }

            outboundHandler = null;
            if (!removed) {
                if (initialMessage != null) {
                    write(initialMessage, false, false);
                    initialMessage = null;
                }

                if (!writtenLast) {
                    writeCompressing(LastHttpContent.EMPTY_LAST_CONTENT, true, outboundAccess.closeAfterWrite);
                }
                requestHandler.responseWritten(outboundAccess.attachment);
                PipeliningServerHandler.this.writeSome();
            }
        }

        @Override
        void discard() {
            super.discard();
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

    private final class BlockingOutboundHandler extends OutboundHandler {
        private static final int QUEUE_SIZE = 2;

        private final HttpResponse response;
        private final InputStream stream;
        private final ExecutorService blockingExecutor;

        private final Queue<ByteBuf> queue = new ArrayDeque<>(QUEUE_SIZE);
        private Future<?> worker = null;
        private boolean workerReady = false;
        private boolean discard = false;
        private boolean done = false;
        private boolean producerWaiting = false;
        private boolean consumerWaiting = false;

        BlockingOutboundHandler(
            OutboundAccess outboundAccess,
            HttpResponse response,
            InputStream stream,
            ExecutorService blockingExecutor) {
            super(outboundAccess);
            this.response = response;
            this.stream = stream;
            this.blockingExecutor = blockingExecutor;
        }

        @Override
        void writeSome() {
            if (worker == null) {
                write(response, false, false);
                worker = blockingExecutor.submit(this::work);
            }
            do {
                ByteBuf msg;
                synchronized (this) {
                    if (producerWaiting) {
                        producerWaiting = false;
                        notifyAll();
                    }
                    msg = queue.poll();
                    if (msg == null && !this.done) {
                        consumerWaiting = true;
                        break;
                    }
                }
                if (msg == null) {
                    // this.done == true inside the synchronized block
                    writeCompressing(LastHttpContent.EMPTY_LAST_CONTENT, true, outboundAccess.closeAfterWrite);

                    outboundHandler = null;
                    requestHandler.responseWritten(outboundAccess.attachment);
                    PipeliningServerHandler.this.writeSome();
                    break;
                } else {
                    writeCompressing(new DefaultHttpContent(msg), true, false);
                }
            } while (ctx.channel().isWritable());
        }

        @Override
        void discard() {
            super.discard();
            discard = true;
            if (worker == null) {
                worker = blockingExecutor.submit(this::work);
            } else {
                synchronized (this) {
                    if (workerReady) {
                        worker.cancel(true);
                        // in case the worker was already done, drain buffers
                        drain();
                    } // else worker is still setting up and will see the discard flag in due time
                }
            }
            // pretend we wrote to clean up resources
            requestHandler.responseWritten(outboundAccess.attachment);
        }

        private void work() {
            ByteBuf buf = null;
            try (InputStream stream = this.stream) {
                synchronized (this) {
                    this.workerReady = true;
                    if (this.discard) {
                        // don't read
                        return;
                    }
                }
                while (true) {
                    buf = ctx.alloc().heapBuffer(LENGTH_8K);
                    int n = buf.writeBytes(stream, LENGTH_8K);
                    synchronized (this) {
                        if (n == -1) {
                            done = true;
                            wakeConsumer();
                            break;
                        }
                        while (queue.size() >= QUEUE_SIZE && !discard) {
                            producerWaiting = true;
                            wait();
                        }
                        if (discard) {
                            break;
                        }
                        queue.add(buf);
                        // buf is now owned by the queue
                        buf = null;

                        wakeConsumer();
                    }
                }
            } catch (InterruptedException | InterruptedIOException ignored) {
            } catch (Exception e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("InputStream threw an error during read. This error cannot be forwarded to the client. Please make sure any errors are thrown by the controller instead.", e);
                }
            } finally {
                // if we failed to add a buffer to the queue, release it
                if (buf != null) {
                    buf.release();
                }
                synchronized (this) {
                    done = true;

                    if (discard) {
                        drain();
                    }
                }
            }
        }

        private void wakeConsumer() {
            assert Thread.holdsLock(this);

            if (!discard && consumerWaiting) {
                consumerWaiting = false;
                ctx.executor().execute(PipeliningServerHandler.this::writeSome);
            }
        }

        private void drain() {
            assert Thread.holdsLock(this);

            ByteBuf buf;
            while (true) {
                buf = queue.poll();
                if (buf != null) {
                    buf.release();
                } else {
                    break;
                }
            }
        }
    }
}
