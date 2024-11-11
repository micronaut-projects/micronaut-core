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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.client.exceptions.ResponseClosedException;
import io.micronaut.http.netty.body.AvailableNettyByteBody;
import io.micronaut.http.netty.body.StreamingNettyByteBody;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Inbound message handler for the HTTP/1.1 client. Also used for HTTP/2 and /3 through message
 * translators.
 *
 * @author Jonas Konrad
 * @since 4.7.0
 */
@Internal
final class Http1ResponseHandler extends SimpleChannelInboundHandlerInstrumented<HttpObject> {
    private static final Logger LOG = LoggerFactory.getLogger(Http1ResponseHandler.class);

    private ReaderState<?> state;

    public Http1ResponseHandler(ResponseListener listener) {
        super(false);
        state = new BeforeResponse(listener);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        ctx.read();
    }

    @Override
    protected void channelReadInstrumented(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg.decoderResult().isFailure()) {
            ReferenceCountUtil.release(msg);
            exceptionCaught(ctx, msg.decoderResult().cause());
            return;
        }

        //noinspection unchecked,rawtypes
        ((ReaderState) state).read(ctx, msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        state.channelReadComplete(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        state.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        state.exceptionCaught(ctx, cause);
    }

    private void transitionToState(ChannelHandlerContext ctx, ReaderState<?> fromState, ReaderState<?> nextState) {
        if (!ctx.executor().inEventLoop()) {
            throw new IllegalStateException("Not on event loop");
        }
        if (state != fromState) {
            throw new IllegalStateException("Wrong source state");
        }
        state = nextState;
    }

    private abstract static sealed class ReaderState<M extends HttpObject> {
        abstract void read(ChannelHandlerContext ctx, M msg);

        void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.read();
        }

        abstract void exceptionCaught(ChannelHandlerContext ctx, Throwable cause);

        void channelInactive(ChannelHandlerContext ctx) {
            exceptionCaught(ctx, new ResponseClosedException("Connection closed before response was received"));
        }
    }

    /**
     * Before any response data has been received.
     */
    private final class BeforeResponse extends ReaderState<HttpResponse> {
        private final ResponseListener listener;

        BeforeResponse(ResponseListener listener) {
            this.listener = listener;
        }

        @Override
        void read(ChannelHandlerContext ctx, HttpResponse msg) {
            ReaderState<HttpContent> nextState;
            if (msg.status().code() == HttpResponseStatus.CONTINUE.code()) {
                listener.continueReceived(ctx);
                nextState = new DiscardingContinueContent(this);
            } else {
                nextState = new BufferedContent(listener, msg);
            }
            transitionToState(ctx, this, nextState);

            if (msg instanceof HttpContent c) {
                nextState.read(ctx, c);
            }
        }

        @Override
        void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            listener.fail(ctx, cause);
        }
    }

    /**
     * After the {@link HttpResponse}, but before the first {@link #channelReadComplete}. We
     * optimistically buffer data until {@link #channelReadComplete} so that we may return it as a
     * more efficient {@link AvailableNettyByteBody}. If there's too much data, fall back to
     * streaming.
     */
    private final class BufferedContent extends ReaderState<HttpContent> {
        private final ResponseListener listener;
        private final HttpResponse response;
        private List<ByteBuf> buffered;

        BufferedContent(ResponseListener listener, HttpResponse response) {
            this.listener = listener;
            this.response = response;
        }

        @Override
        void read(ChannelHandlerContext ctx, HttpContent msg) {
            if (msg.content().isReadable()) {
                if (buffered == null) {
                    buffered = new ArrayList<>();
                }
                buffered.add(msg.content());
            } else {
                msg.release();
            }
            if (msg instanceof LastHttpContent) {
                List<ByteBuf> buffered = this.buffered;
                this.buffered = null;
                transitionToState(ctx, this, AfterContent.INSTANCE);
                BodySizeLimits limits = listener.sizeLimits();
                if (buffered == null) {
                    complete(AvailableNettyByteBody.empty());
                } else if (buffered.size() == 1) {
                    complete(AvailableNettyByteBody.createChecked(ctx.channel().eventLoop(), limits, buffered.get(0)));
                } else {
                    CompositeByteBuf composite = ctx.alloc().compositeBuffer();
                    composite.addComponents(true, buffered);
                    complete(AvailableNettyByteBody.createChecked(ctx.channel().eventLoop(), limits, composite));
                }
                listener.finish(ctx);
            }
        }

        @Override
        void channelReadComplete(ChannelHandlerContext ctx) {
            devolveToStreaming(ctx);
            state.channelReadComplete(ctx); // check if there's demand
        }

        @Override
        void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            devolveToStreaming(ctx);
            state.exceptionCaught(ctx, cause);
        }

        private void devolveToStreaming(ChannelHandlerContext ctx) {
            assert ctx.executor().inEventLoop();

            UnbufferedContent unbufferedContent = new UnbufferedContent(listener, ctx, response);
            if (buffered != null) {
                for (ByteBuf buf : buffered) {
                    unbufferedContent.add(buf);
                }
            }
            transitionToState(ctx, this, unbufferedContent);
            complete(new StreamingNettyByteBody(unbufferedContent.streaming));
        }

        private void complete(CloseableByteBody body) {
            assert state != this : "should have been replaced already";
            listener.complete(response, body);
        }
    }

    /**
     * Normal content handler, streaming data into a {@link StreamingNettyByteBody}.
     */
    private final class UnbufferedContent extends ReaderState<HttpContent> implements BufferConsumer.Upstream {
        private final ResponseListener listener;
        private final ChannelHandlerContext streamingContext;
        private final StreamingNettyByteBody.SharedBuffer streaming;
        private long demand;

        UnbufferedContent(ResponseListener listener, ChannelHandlerContext ctx, HttpResponse response) {
            this.listener = listener;
            streaming = new StreamingNettyByteBody.SharedBuffer(ctx.channel().eventLoop(), listener.sizeLimits(), this);
            if (!listener.isHeadResponse()) {
                streaming.setExpectedLengthFrom(response.headers());
            }
            streamingContext = ctx;
        }

        void add(ByteBuf buf) {
            if (buf.isReadable()) {
                demand -= buf.readableBytes();
                streaming.add(buf);
            } else {
                buf.release();
            }
        }

        @Override
        void read(ChannelHandlerContext ctx, HttpContent msg) {
            add(msg.content());
            if (msg instanceof LastHttpContent) {
                transitionToState(ctx, this, AfterContent.INSTANCE);
                streaming.complete();
                listener.finish(ctx);
            }
        }

        @Override
        void channelReadComplete(ChannelHandlerContext ctx) {
            if (demand > 0) {
                ctx.read();
            }
        }

        @Override
        void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            streaming.error(cause);
        }

        @Override
        public void start() {
            if (streamingContext.executor().inEventLoop()) {
                start0();
            } else {
                streamingContext.executor().execute(this::start0);
            }
        }

        private void start0() {
            if (state != this) {
                return;
            }

            demand++;
            if (demand == 1) {
                streamingContext.read();
            }
        }

        @Override
        public void onBytesConsumed(long bytesConsumed) {
            if (streamingContext.executor().inEventLoop()) {
                onBytesConsumed0(bytesConsumed);
            } else {
                streamingContext.executor().execute(() -> onBytesConsumed0(bytesConsumed));
            }
        }

        private void onBytesConsumed0(long bytesConsumed) {
            if (state != this) {
                return;
            }

            long oldDemand = demand;
            long newDemand = oldDemand + bytesConsumed;
            if (newDemand < oldDemand) {
                // overflow
                newDemand = oldDemand;
            }
            this.demand = newDemand;
            if (oldDemand <= 0 && newDemand > 0) {
                streamingContext.read();
            }
        }

        @Override
        public void allowDiscard() {
            if (streamingContext.executor().inEventLoop()) {
                allowDiscard0();
            } else {
                streamingContext.executor().execute(this::allowDiscard0);
            }
        }

        private void allowDiscard0() {
            if (state == this) {
                transitionToState(streamingContext, this, new DiscardingContent(listener, streaming));
                disregardBackpressure();
            }
            listener.allowDiscard();
        }

        @Override
        public void disregardBackpressure() {
            if (streamingContext.executor().inEventLoop()) {
                disregardBackpressure0();
            } else {
                streamingContext.executor().execute(this::disregardBackpressure0);
            }
        }

        private void disregardBackpressure0() {
            long oldDemand = demand;
            demand = Long.MAX_VALUE;
            if (oldDemand <= 0 && state == this) {
                streamingContext.read();
            }
        }
    }

    /**
     * Short-circuiting handler that discards incoming content.
     */
    private final class DiscardingContent extends ReaderState<HttpContent> {
        private final ResponseListener listener;
        private final StreamingNettyByteBody.SharedBuffer streaming;

        DiscardingContent(ResponseListener listener, StreamingNettyByteBody.SharedBuffer streaming) {
            this.listener = listener;
            this.streaming = streaming;
        }

        @Override
        void read(ChannelHandlerContext ctx, HttpContent msg) {
            msg.release();
            if (msg instanceof LastHttpContent) {
                transitionToState(ctx, this, AfterContent.INSTANCE);
                listener.finish(ctx);
            }
        }

        @Override
        void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            streaming.error(cause);
        }
    }

    /**
     * Short-circuiting handler that discards incoming content of a CONTINUE response.
     */
    private final class DiscardingContinueContent extends ReaderState<HttpContent> {
        private final BeforeResponse beforeResponse;

        DiscardingContinueContent(BeforeResponse beforeResponse) {
            this.beforeResponse = beforeResponse;
        }

        @Override
        void read(ChannelHandlerContext ctx, HttpContent msg) {
            msg.release();
            if (msg instanceof LastHttpContent) {
                transitionToState(ctx, this, this.beforeResponse);
            }
        }

        @Override
        void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            this.beforeResponse.exceptionCaught(ctx, cause);
        }
    }

    /**
     * Special handler that is used after the {@link LastHttpContent}. There should be no more
     * incoming messages at this point.
     */
    private static final class AfterContent extends ReaderState<HttpContent> {
        static final AfterContent INSTANCE = new AfterContent();

        @Override
        void read(ChannelHandlerContext ctx, HttpContent msg) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Discarding unexpected message {}", msg);
            }
            ReferenceCountUtil.release(msg);
        }

        @Override
        void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.fireExceptionCaught(cause);
        }

        @Override
        void channelInactive(ChannelHandlerContext ctx) {
            ctx.fireChannelInactive();
        }
    }

    /**
     * The response listener.
     */
    interface ResponseListener {
        /**
         * Size limits for the request body.
         *
         * @return The size limits
         */
        @NonNull
        default BodySizeLimits sizeLimits() {
            return BodySizeLimits.UNLIMITED;
        }

        /**
         * {@code true} iff we expect a response to a HEAD request. This influences handling of
         * {@code Content-Length}.
         *
         * @return {@code true} iff this is a HEAD response
         */
        default boolean isHeadResponse() {
            return false;
        }

        /**
         * Called when the handler receives a {@code CONTINUE} response, so the listener should
         * proceed with sending the request body.
         *
         * @param ctx The handler context
         */
        default void continueReceived(@NonNull ChannelHandlerContext ctx) {
        }

        /**
         * Called when there is a failure <i>before</i>
         * {@link #complete(HttpResponse, CloseableByteBody)} is called, i.e. we didn't even
         * receive (valid) headers.
         *
         * @param ctx The handler context
         * @param t The failure
         */
        void fail(@NonNull ChannelHandlerContext ctx, @NonNull Throwable t);

        /**
         * Called when the headers (and potentially some or all of the body) are fully received.
         *
         * @param response The response status, headers...
         * @param body The response body, potentially streaming
         */
        void complete(@NonNull HttpResponse response, @NonNull CloseableByteBody body);

        /**
         * Called when the last piece of the body is received. This handler can be removed and the
         * connection can be returned to the connection pool.
         *
         * @param ctx The handler context
         */
        void finish(@NonNull ChannelHandlerContext ctx);

        /**
         * Called when the body passed to {@link #complete(HttpResponse, CloseableByteBody)} has
         * been discarded. We may want to close the connection in that case to avoid having to
         * receive unnecessary data.
         */
        default void allowDiscard() {
        }
    }
}
