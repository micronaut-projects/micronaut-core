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
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.netty.EventLoopFlow;
import io.micronaut.http.netty.body.AvailableNettyByteBody;
import io.micronaut.http.netty.body.ByteBufConsumer;
import io.micronaut.http.netty.body.NettyBodyAdapter;
import io.micronaut.http.netty.body.NettyByteBody;
import io.micronaut.http.netty.body.StreamingNettyByteBody;
import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.micronaut.http.server.netty.HttpCompressionStrategy;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Queue;

/**
 * Netty handler that handles incoming {@link HttpRequest}s and forwards them to a
 * {@link RequestHandler}.
 *
 * @author Jonas Konrad
 * @since 4.0.0
 */
@Internal
public final class PipeliningServerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(PipeliningServerHandler.class);

    private final RequestHandler requestHandler;

    // these three handlers can be reused and are cached here
    private final DroppingInboundHandler droppingInboundHandler = new DroppingInboundHandler();
    private final InboundHandler baseInboundHandler = new MessageInboundHandler();
    private final OptimisticBufferingInboundHandler optimisticBufferingInboundHandler = new OptimisticBufferingInboundHandler();

    private Compressor compressor;
    private BodySizeLimits bodySizeLimits = BodySizeLimits.UNLIMITED;

    /**
     * Current handler for inbound messages.
     */
    private InboundHandler inboundHandler = baseInboundHandler;

    /**
     * Queue of outbound messages that can't be written yet.
     */
    private final Queue<OutboundAccessImpl> outboundQueue = new ArrayDeque<>(1);
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
        if (compressionStrategy.isEnabled()) {
            this.compressor = new Compressor(compressionStrategy);
        } else {
            this.compressor = null;
        }
    }

    public void setBodySizeLimits(BodySizeLimits bodySizeLimits) {
        this.bodySizeLimits = bodySizeLimits;
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
            outboundHandler.discardOutbound();
        }
        for (OutboundAccessImpl queued : outboundQueue) {
            if (queued.handler != null) {
                queued.handler.discardOutbound();
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
                    OutboundAccessImpl next = outboundQueue.peek();
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
            OutboundAccessImpl outboundAccess = new OutboundAccessImpl(request);
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

            // getClass for performance
            boolean full = request.getClass() != DefaultHttpRequest.class && request instanceof FullHttpRequest;
            if (full && decompressionChannel == null) {
                requestHandler.accept(ctx, request, AvailableNettyByteBody.createChecked(ctx.channel().eventLoop(), bodySizeLimits, ((FullHttpRequest) request).content()), outboundAccess);
            } else if (!hasBody(request)) {
                inboundHandler = droppingInboundHandler;
                if (full) {
                    inboundHandler.read(message);
                }
                if (decompressionChannel != null) {
                    decompressionChannel.finish();
                }
                requestHandler.accept(ctx, request, AvailableNettyByteBody.empty(), outboundAccess);
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
        private OutboundAccessImpl outboundAccess;
        private final List<HttpContent> buffer = new ArrayList<>();

        void init(HttpRequest request, OutboundAccessImpl outboundAccess) {
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
            // exact class check to avoid interface instanceof
            if (message.getClass() == DefaultLastHttpContent.class || message instanceof LastHttpContent) {
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
                requestHandler.accept(ctx, request, AvailableNettyByteBody.createChecked(ctx.channel().eventLoop(), bodySizeLimits, fullBody), outboundAccess);

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
            HttpRequest request = this.request;
            OutboundAccessImpl outboundAccess = this.outboundAccess;
            this.request = null;
            this.outboundAccess = null;

            StreamingInboundHandler streamingInboundHandler = new StreamingInboundHandler(outboundAccess, HttpUtil.is100ContinueExpected(request));
            for (HttpContent content : buffer) {
                streamingInboundHandler.read(content);
            }
            buffer.clear();

            if (inboundHandler == this) {
                inboundHandler = streamingInboundHandler;
            } else {
                ((DecompressingInboundHandler) inboundHandler).delegate = streamingInboundHandler;
            }
            streamingInboundHandler.dest.setExpectedLengthFrom(request.headers());
            requestHandler.accept(ctx, request, new StreamingNettyByteBody(streamingInboundHandler.dest), outboundAccess);
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

        private StreamingInboundHandler(OutboundAccessImpl outboundAccess, boolean sendContinue) {
            this.outboundAccess = outboundAccess;
            this.sendContinue = sendContinue;
            this.dest = new StreamingNettyByteBody.SharedBuffer(ctx.channel().eventLoop(), bodySizeLimits, this);
        }

        @Override
        void read(Object message) {
            HttpContent content = (HttpContent) message;
            requested -= content.content().readableBytes();
            dest.add(content.content());
            if (message instanceof LastHttpContent) {
                dest.complete();
                inboundHandler = baseInboundHandler;
            }
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
            EventLoop eventLoop = ctx.channel().eventLoop();
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
            EventLoop eventLoop = ctx.channel().eventLoop();
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
            EventLoop eventLoop = ctx.channel().eventLoop();
            if (!eventLoop.inEventLoop()) {
                eventLoop.execute(this::allowDiscard);
                return;
            }

            sendContinue = false;
            if (inboundHandler == this) {
                inboundHandler = droppingInboundHandler;
                refreshNeedMore();
            } else if (inboundHandler instanceof DecompressingInboundHandler dec && dec.delegate == this) {
                dec.dispose();
                inboundHandler = droppingInboundHandler;
                refreshNeedMore();
            }
            dest.discard();
        }

        @Override
        public void disregardBackpressure() {
            EventLoop eventLoop = ctx.channel().eventLoop();
            if (!eventLoop.inEventLoop()) {
                eventLoop.execute(this::disregardBackpressure);
                return;
            }

            requested = Long.MAX_VALUE;
            refreshNeedMore();
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

            boolean last = message instanceof LastHttpContent;
            try {
                channel.writeInbound(compressed);
                if (last) {
                    channel.finish();
                }
            } catch (DecompressionException e) {
                delegate.handleUpstreamError(e);
                channel.releaseInbound();
                if (last) {
                    // need to handle the last content
                    inboundHandler.read(LastHttpContent.EMPTY_LAST_CONTENT);
                }
                return;
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
            if (message instanceof LastHttpContent lhc) {
                lhc.release();
                inboundHandler = baseInboundHandler;
            } else {
                ((HttpContent) message).release();
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
    public final class OutboundAccessImpl implements OutboundAccess {
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

        private OutboundAccessImpl(HttpRequest request) {
            this.request = request;
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
        public void writeHeadResponse(@NonNull HttpResponse response) {
            writeFull(new DefaultFullHttpResponse(
                response.protocolVersion(),
                response.status(),
                Unpooled.EMPTY_BUFFER,
                response.headers(),
                EmptyHttpHeaders.INSTANCE
            ), true);
        }

        private void writeFull(FullHttpResponse response, boolean headResponse) {
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
            if (response.content().isReadable()) {
                prepareCompression(response, oh, response.content().readableBytes());
            }
            write(oh);
        }

        @Override
        public void write(@NonNull HttpResponse response, @NonNull ByteBody body) {
            NettyByteBody nbb = NettyBodyAdapter.adapt(body, ctx.channel().eventLoop());
            if (nbb instanceof AvailableNettyByteBody available) {
                writeFull(new DefaultFullHttpResponse(response.protocolVersion(), response.status(), AvailableNettyByteBody.toByteBuf(available), response.headers(), EmptyHttpHeaders.INSTANCE), false);
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
                    if (canHaveBody(response.status())) {
                        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                    } else {
                        response.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
                    }
                }
                preprocess(response);
                StreamingOutboundHandler oh = new StreamingOutboundHandler(this, response);
                prepareCompression(response, oh, expectedLength.orElse(-1));
                oh.upstream = ((StreamingNettyByteBody) nbb).primary(oh);
                write(oh);
            }
        }

        private void prepareCompression(HttpResponse response, OutboundHandler outboundHandler, long contentLength) {
            if (compressor == null) {
                return;
            }
            Compressor.Session compressionSession = compressor.prepare(ctx, request, response, contentLength);
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

    private abstract class OutboundHandler {
        /**
         * {@link OutboundAccessImpl} that created this handler, for metadata access.
         */
        final OutboundAccessImpl outboundAccess;

        Compressor.Session compressionSession;

        private OutboundHandler(OutboundAccessImpl outboundAccess) {
            this.outboundAccess = outboundAccess;
        }

        protected final void writeCompressing(HttpContent content, @SuppressWarnings("SameParameterValue") boolean flush, boolean close) {
            if (this.compressionSession == null) {
                write(content, flush, close);
            } else {
                // slow path
                writeCompressing0(content, flush, close);
            }
        }

        private void writeCompressing0(HttpContent content, boolean flush, boolean close) {
            Compressor.Session compressionSession = this.compressionSession;
            compressionSession.push(content.content());
            boolean last = content instanceof LastHttpContent;
            if (last) {
                compressionSession.finish();
            }
            if (content instanceof HttpResponse hr) {
                assert last;

                compressionSession.fixContentLength(hr);

                // this can happen in FullHttpResponse, just send the full body.
                write(new DefaultHttpResponse(hr.protocolVersion(), hr.status(), hr.headers()), false, false);
            }
            ByteBuf toSend = compressionSession.poll();
            // send the compressed buffer with the flags.
            if (toSend == null) {
                if (last) {
                    HttpHeaders trailingHeaders = ((LastHttpContent) content).trailingHeaders();
                    write(trailingHeaders.isEmpty() ? LastHttpContent.EMPTY_LAST_CONTENT : new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER, trailingHeaders), flush, close);
                } else if (flush || close) {
                    // not sure if this can actually happen, but we need to forward a flush/close
                    write(new DefaultHttpContent(Unpooled.EMPTY_BUFFER), flush, close);
                } // else just don't send anything
            } else {
                if (last) {
                    write(new DefaultLastHttpContent(toSend, ((LastHttpContent) content).trailingHeaders()), flush, close);
                } else {
                    write(new DefaultHttpContent(toSend), flush, close);
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
        void discardOutbound() {
            Compressor.Session compressionSession = this.compressionSession;
            if (compressionSession != null) {
                compressionSession.discard();
            }
        }
    }

    /**
     * Handler that writes a 100 CONTINUE response and then proceeds with the {@link #next} handler.
     */
    final class ContinueOutboundHandler extends OutboundHandler {
        static final FullHttpResponse CONTINUE_11 =
            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER);
        private static final FullHttpResponse CONTINUE_10 =
            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER);

        boolean written = false;
        OutboundHandler next;

        private ContinueOutboundHandler(OutboundAccessImpl outboundAccess) {
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
        void discardOutbound() {
            super.discardOutbound();
            if (next != null) {
                next.discardOutbound();
                next = null;
            }
        }
    }

    /**
     * Handler that writes a {@link FullHttpResponse}.
     */
    private final class FullOutboundHandler extends OutboundHandler {
        private final FullHttpResponse message;

        FullOutboundHandler(OutboundAccessImpl outboundAccess, FullHttpResponse message) {
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
        void discardOutbound() {
            super.discardOutbound();
            outboundHandler = null;
            // pretend we wrote to clean up resources
            requestHandler.responseWritten(outboundAccess.attachment);
            message.release();
        }
    }

    /**
     * Handler that writes a {@link StreamedHttpResponse}.
     */
    private final class StreamingOutboundHandler extends OutboundHandler implements ByteBufConsumer {
        private final EventLoopFlow flow = new EventLoopFlow(ctx.channel().eventLoop());
        private final OutboundAccessImpl outboundAccess;
        private HttpResponse initialMessage;
        private BufferConsumer.Upstream upstream;
        private boolean earlyComplete = false;
        private boolean writtenLast = false;
        private long incompleteWrittenBytes = 0;

        StreamingOutboundHandler(OutboundAccessImpl outboundAccess, HttpResponse initialMessage) {
            super(outboundAccess);
            assert initialMessage != null;
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
                upstream.start();
            }
            if (earlyComplete) {
                // onComplete has been called before the first writeSome. Trigger onComplete
                // handling again.
                complete();
            } else {
                long written = incompleteWrittenBytes;
                if (written > 0) {
                    incompleteWrittenBytes = 0;
                    upstream.onBytesConsumed(written);
                }
            }
        }

        @Override
        public void add(ByteBuf buf) {
            if (flow.executeNow(() -> add0(buf))) {
                add0(buf);
            }
        }

        private void add0(ByteBuf buf) {
            if (outboundHandler != this) {
                throw new IllegalStateException("onNext before request?");
            }

            if (writtenLast) {
                throw new IllegalStateException("Already written a LastHttpContent");
            }

            if (!removed) {
                int n = buf.readableBytes();
                writeCompressing(new DefaultHttpContent(buf), true, false);
                incompleteWrittenBytes += n;
                if (ctx.channel().isWritable()) {
                    writeSome();
                }
            } else {
                buf.release();
            }
        }

        @Override
        public void error(Throwable t) {
            if (flow.executeNow(() -> error0(t))) {
                error0(t);
            }
        }

        private void error0(Throwable t) {
            if (!removed) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Reactive response received an error after some data has already been written. This error cannot be forwarded to the client.", t);
                }
                ctx.close();

                requestHandler.responseWritten(outboundAccess.attachment);
            }
        }

        @Override
        public void complete() {
            if (flow.executeNow(this::complete0)) {
                complete0();
            }
        }

        private void complete0() {
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
        void discardOutbound() {
            super.discardOutbound();
            // this is safe because:
            // - onComplete/onError cannot have been called yet, because otherwise outboundHandler
            //   would be null and discard couldn't have been called
            // - while cancel() may trigger onComplete/onError, `removed` is true at this point, so
            //   they won't call responseWritten in turn
            requestHandler.responseWritten(outboundAccess.attachment);
            upstream.allowDiscard();
            outboundHandler = null;
        }
    }
}
