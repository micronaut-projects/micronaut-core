/*
 * Copyright 2017-2026 original authors
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
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpStatusClass;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.AttributeKey;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * The read timeout of one exchange on an HTTP/1 connection, see
 * {@link io.micronaut.http.client.RawRequestOptions#getReadIdleTimeout()}: while it is in the
 * pipeline, the read timeout of the connection is suspended, and it removes itself once the
 * response ends.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class RequestReadIdleTimeoutHandler extends ReadTimeoutHandler {
    /**
     * The name of the handler in a pipeline.
     */
    static final String NAME = "micronaut-request-read-idle-timeout";
    /**
     * Set on a connection while an exchange has its own read timeout: the read timeout of the
     * connection does not fire.
     */
    static final AttributeKey<Boolean> SUSPENDS_CONNECTION_READ_TIMEOUT = AttributeKey.valueOf(RequestReadIdleTimeoutHandler.class, "suspends-connection-read-timeout");

    private boolean timedOut;
    private boolean inInterim;

    RequestReadIdleTimeoutHandler(Duration timeout) {
        super(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    /**
     * Give an exchange its own read timeout. The stream of an HTTP/2 exchange gets it in place
     * of the read timeout of the client; an HTTP/1 connection suspends its own read timeout while
     * the exchange runs.
     *
     * @param http2           Whether the exchange is an HTTP/2 stream
     * @param pipeline        The pipeline of the exchange
     * @param readIdleTimeout The read timeout of the exchange
     */
    static void install(boolean http2, ChannelPipeline pipeline, Duration readIdleTimeout) {
        if (http2) {
            ChannelHandler current = pipeline.get(ChannelPipelineCustomizer.HANDLER_READ_TIMEOUT);
            StreamReadTimeoutHandler.Connection connection = current instanceof StreamReadTimeoutHandler stream
                ? stream.connection() : StreamReadTimeoutHandler.Connection.NONE;
            StreamReadTimeoutHandler handler = new StreamReadTimeoutHandler(readIdleTimeout, connection);
            if (current != null) {
                pipeline.replace(current, ChannelPipelineCustomizer.HANDLER_READ_TIMEOUT, handler);
            } else {
                pipeline.addFirst(ChannelPipelineCustomizer.HANDLER_READ_TIMEOUT, handler);
            }
        } else {
            pipeline.addBefore(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE, RequestReadIdleTimeoutHandler.NAME,
                new RequestReadIdleTimeoutHandler(readIdleTimeout));
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().attr(SUSPENDS_CONNECTION_READ_TIMEOUT).set(Boolean.TRUE);
        super.handlerAdded(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().attr(SUSPENDS_CONNECTION_READ_TIMEOUT).set(null);
        super.handlerRemoved(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // an interim response, e.g. 100 Continue or 103 Early Hints, ends with its own last
        // content, but the exchange goes on
        if (msg instanceof HttpResponse response) {
            inInterim = response.status().codeClass() == HttpStatusClass.INFORMATIONAL
                && response.status().code() != HttpResponseStatus.SWITCHING_PROTOCOLS.code();
        }
        boolean last = msg instanceof LastHttpContent && !inInterim;
        if (msg instanceof LastHttpContent) {
            inInterim = false;
        }
        super.channelRead(ctx, msg);
        if (last && ctx.pipeline().context(this) != null) {
            // the response ended: the next exchange of the connection has the read timeout of the connection
            ctx.pipeline().remove(this);
        }
    }

    @Override
    protected void readTimedOut(ChannelHandlerContext ctx) {
        if (timedOut) {
            return;
        }
        timedOut = true;
        ctx.fireExceptionCaught(ReadTimeoutException.INSTANCE);
        ctx.close();
    }
}
