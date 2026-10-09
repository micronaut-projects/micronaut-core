/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.exceptions.UriSyntaxException;
import io.micronaut.http.netty.NettyHttpResponseBuilder;
import io.micronaut.http.server.util.HttpHostResolver;
import io.micronaut.http.ssl.ServerSslConfiguration;
import io.micronaut.http.uri.UriBuilder;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Handler to automatically redirect HTTP to HTTPS request when using dual protocol.
 *
 * @author Iván López
 * @since 2.5.0
 */
@ChannelHandler.Sharable
@Internal
final class HttpToHttpsRedirectHandler extends ChannelDuplexHandler {

    private static final Logger LOG = LoggerFactory.getLogger(HttpToHttpsRedirectHandler.class);

    private final ServerSslConfiguration sslConfiguration;
    private final HttpHostResolver hostResolver;

    /**
     * Construct HttpToHttpsRedirectHandler for the given arguments.
     *
     * @param sslConfiguration The {@link ServerSslConfiguration}
     * @param hostResolver     The {@link HttpHostResolver}
     */
    public HttpToHttpsRedirectHandler(ServerSslConfiguration sslConfiguration,
                                      HttpHostResolver hostResolver) {
        this.hostResolver = hostResolver;
        this.sslConfiguration = sslConfiguration;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest && ctx.pipeline().get(SslHandler.class) == null) {
            HttpRequest<?> request = (HttpRequest<?>) msg;
            URI location;
            try {
                location = location(request);
            } catch (IllegalArgumentException | UriSyntaxException e) {
                // the host the request resolves to is not a valid URI authority. No redirect
                // can be built, so answer with an empty 400 and close the connection
                LOG.debug("Cannot redirect a request with an invalid URI or host to HTTPS", e);
                writeBadRequest(ctx);
                return;
            }

            MutableHttpResponse<?> response = HttpResponse
                    .permanentRedirect(location)
                    .header(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            io.netty.handler.codec.http.HttpResponse nettyResponse = NettyHttpResponseBuilder.toHttpResponse(response);
            ctx.writeAndFlush(nettyResponse);
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (ctx.pipeline().get(SslHandler.class) == null && isInvalidUri(cause)) {
            // the request target is not a valid URI, e.g. a query containing characters
            // java.net.URI rejects. The request decoder reports it as an exception before the
            // request reaches channelRead. This listener only redirects, so answer with an empty
            // 400 and close the connection, like for an invalid host
            LOG.debug("Cannot redirect a request with an invalid URI to HTTPS", cause);
            // the request decoder registered a placeholder request for the error handling
            NettyHttpRequest.remove(ctx);
            writeBadRequest(ctx);
        } else {
            super.exceptionCaught(ctx, cause);
        }
    }

    private URI location(HttpRequest<?> request) {
        UriBuilder uriBuilder = UriBuilder.of(hostResolver.resolve(request));
        uriBuilder.scheme("https");
        int port = sslConfiguration.getPort();
        if (port == 443) {
            uriBuilder.port(-1);
        } else {
            uriBuilder.port(port);
        }
        uriBuilder.path(request.getPath());
        return uriBuilder.build();
    }

    private static boolean isInvalidUri(Throwable cause) {
        return cause instanceof URISyntaxException || cause instanceof UriSyntaxException;
    }

    private static void writeBadRequest(ChannelHandlerContext ctx) {
        MutableHttpResponse<?> response = HttpResponse.status(HttpStatus.BAD_REQUEST)
                .header(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(NettyHttpResponseBuilder.toHttpResponse(response));
    }
}
