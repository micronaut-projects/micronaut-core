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

import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.exceptions.ResponseClosedException;
import io.micronaut.http.netty.body.AvailableNettyByteBody;
import io.micronaut.http.netty.body.BodySizeLimits;
import io.micronaut.http.netty.body.BufferConsumer;
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
final class Http1ResponseHandler extends SimpleChannelInboundHandlerInstrumented<HttpObject> implements BufferConsumer.Upstream {
    private static final Logger LOG = LoggerFactory.getLogger(Http1ResponseHandler.class);

    private final ResponseListener listener;
    private State state = State.BEFORE_RESPONSE;
    private HttpResponse response;
    private List<ByteBuf> buffered;
    private StreamingNettyByteBody.SharedBuffer streaming;
    private long demand;
    private ChannelHandlerContext streamingContext;

    public Http1ResponseHandler(ResponseListener listener) {
        super(false);
        this.listener = listener;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        ctx.read();
    }

    @Override
    protected void channelReadInstrumented(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg.decoderResult().isFailure()) {
            ReferenceCountUtil.release(msg);
            exceptionCaught(ctx, msg.decoderResult().cause());
            return;
        }

        HttpContent content;
        switch (state) {
            case BEFORE_RESPONSE:
                HttpResponse response = (HttpResponse) msg;
                if (response.status().code() == HttpResponseStatus.CONTINUE.code()) {
                    listener.continueReceived(ctx);
                    if (response instanceof LastHttpContent lhc) {
                        lhc.release();
                    } else {
                        assert !(response instanceof HttpContent);
                        state = State.DISCARDING_CONTINUE_CONTENT;
                    }
                    break;
                }
                this.response = response;
                state = State.BUFFERED_CONTENT;
                // if the response is a FullHttpResponse, fall through to BUFFERED_CONTENT handling
                if (!(msg instanceof HttpContent)) {
                    break;
                }
                // fall-through
            case BUFFERED_CONTENT:
                content = (HttpContent) msg;
                content.touch();
                if (content.content().isReadable()) {
                    if (buffered == null) {
                        buffered = new ArrayList<>();
                    }
                    buffered.add(content.content());
                } else {
                    content.release();
                }
                if (content instanceof LastHttpContent) {
                    List<ByteBuf> buffered = this.buffered;
                    this.buffered = null;
                    state = State.AFTER_CONTENT;
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
                break;
            case UNBUFFERED_CONTENT:
                content = (HttpContent) msg;
                content.touch();
                if (content.content().isReadable()) {
                    demand -= content.content().readableBytes();
                    streaming.add(content.content());
                } else {
                    content.release();
                }
                if (content instanceof LastHttpContent) {
                    state = State.AFTER_CONTENT;
                    streaming.complete();
                    listener.finish(ctx);
                }
                break;
            case DISCARDING_CONTENT:
                ((HttpContent) msg).content().release();
                if (msg instanceof LastHttpContent) {
                    state = State.AFTER_CONTENT;
                    listener.finish(ctx);
                }
                break;
            case DISCARDING_CONTINUE_CONTENT:
                ((HttpContent) msg).content().release();
                if (msg instanceof LastHttpContent) {
                    state = State.BEFORE_RESPONSE;
                }
                break;
            case AFTER_CONTENT:
                LOG.warn("Discarding unexpected message {}", msg);
                ReferenceCountUtil.release(msg);
                break;
            default:
                throw new AssertionError(state);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if (state == State.BUFFERED_CONTENT) {
            devolveToStreaming(ctx);
        }
        if (state != State.AFTER_CONTENT && demand > 0) {
            ctx.read();
        } else if (state == State.BEFORE_RESPONSE || state == State.DISCARDING_CONTINUE_CONTENT || state == State.DISCARDING_CONTENT) {
            ctx.read();
        }
    }

    private void devolveToStreaming(ChannelHandlerContext ctx) {
        assert state == State.BUFFERED_CONTENT;
        assert ctx.executor().inEventLoop();

        streaming = new StreamingNettyByteBody.SharedBuffer(ctx.channel().eventLoop(), listener.sizeLimits(), this);
        if (!listener.isHeadResponse()) {
            streaming.setExpectedLengthFrom(response.headers());
        }
        streamingContext = ctx;
        if (buffered != null) {
            for (ByteBuf buf : buffered) {
                demand -= buf.readableBytes();
                streaming.add(buf);
            }
            buffered = null;
        }
        state = State.UNBUFFERED_CONTENT;
        complete(new StreamingNettyByteBody(streaming));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (state != State.AFTER_CONTENT) {
            exceptionCaught(ctx, new ResponseClosedException("Connection closed before response was received"));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        switch (state) {
            case BEFORE_RESPONSE:
            case DISCARDING_CONTINUE_CONTENT:
                listener.fail(ctx, cause);
                break;
            case BUFFERED_CONTENT:
                if (buffered != null) {
                    for (ByteBuf buf : buffered) {
                        buf.release();
                    }
                    buffered = null;
                }
                devolveToStreaming(ctx);
                // fall through
            case UNBUFFERED_CONTENT:
            case DISCARDING_CONTENT:
                streaming.error(cause);
                break;
            case AFTER_CONTENT:
                ctx.fireExceptionCaught(cause);
                break;
            default:
                throw new AssertionError(state);
        }
    }

    @Override
    public void start() {
        assert streamingContext.executor().inEventLoop();

        demand++;
        if (demand == 1) {
            streamingContext.read();
        }
    }

    @Override
    public void onBytesConsumed(long bytesConsumed) {
        assert streamingContext.executor().inEventLoop();

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
        assert streamingContext.executor().inEventLoop();

        if (state == State.UNBUFFERED_CONTENT || state == State.BUFFERED_CONTENT) {
            state = State.DISCARDING_CONTENT;
            disregardBackpressure();
        }
        listener.allowDiscard();
    }

    @Override
    public void disregardBackpressure() {
        assert streamingContext.executor().inEventLoop();

        long oldDemand = demand;
        demand = Long.MAX_VALUE;
        if (oldDemand <= 0 && state == State.UNBUFFERED_CONTENT) {
            streamingContext.read();
        }
    }

    private void complete(CloseableByteBody body) {
        listener.complete(response, body);
    }

    private enum State {
        BEFORE_RESPONSE,
        DISCARDING_CONTINUE_CONTENT,
        BUFFERED_CONTENT,
        UNBUFFERED_CONTENT,
        DISCARDING_CONTENT,
        AFTER_CONTENT,
    }

    /**
     * The response listener.
     */
    public interface ResponseListener {
        /**
         * Size limits for the request body.
         *
         * @return The size limits
         */
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
        default void continueReceived(ChannelHandlerContext ctx) {
        }

        /**
         * Called when there is a failure <i>before</i>
         * {@link #complete(HttpResponse, CloseableByteBody)} is called, i.e. we didn't even
         * receive (valid) headers.
         *
         * @param ctx The handler context
         * @param t The failure
         */
        void fail(ChannelHandlerContext ctx, Throwable t);

        /**
         * Called when the headers (and potentially some or all of the body) are fully received.
         *
         * @param response The response status, headers...
         * @param body The response body, potentially streaming
         */
        void complete(HttpResponse response, CloseableByteBody body);

        /**
         * Called when the last piece of the body is received. This handler can be removed and the
         * connection can be returned to the connection pool.
         *
         * @param ctx The handler context
         */
        void finish(ChannelHandlerContext ctx);

        /**
         * Called when the body passed to {@link #complete(HttpResponse, CloseableByteBody)} has
         * been discarded. We may want to close the connection in that case to avoid having to
         * receive unnecessary data.
         */
        default void allowDiscard() {
        }
    }
}
