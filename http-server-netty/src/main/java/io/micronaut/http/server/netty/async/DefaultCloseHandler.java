/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.http.server.netty.async;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;

/**
 * A future that executes the standard close procedure.
 *
 * @author James Kleeh
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultCloseHandler implements GenericFutureListener<ChannelFuture> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultCloseHandler.class);

    private final ChannelHandlerContext context;
    private final HttpRequest<?> request;
    private final int statusCode;

    /**
     * @param context    The channel handler context
     * @param request    The Http request
     * @param statusCode The status code
     */
    public DefaultCloseHandler(
        ChannelHandlerContext context,
        HttpRequest<?> request,
        int statusCode) {

        this.context = context;
        this.request = request;
        this.statusCode = statusCode;
    }

    @Override
    public void operationComplete(ChannelFuture future) {
        if (!future.isSuccess()) {
            Throwable cause = future.cause();
            // swallow closed channel exception, nothing we can do about it if the client disconnects
            if (!(cause instanceof ClosedChannelException)) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error writing Netty response: " + cause.getMessage(), cause);
                }

                // if we have arrived to this point something has gone wrong streaming the response the client
                // so we just queue an internal server error response to return to the client
                context.writeAndFlush(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR))
                    .addListener(ChannelFutureListener.CLOSE);
            }
        } else if (!request.getHeaders().isKeepAlive() || statusCode >= HttpStatus.MULTIPLE_CHOICES.getCode()) {
            future.channel().close();
        } else {
            context.read();
        }
    }
}
