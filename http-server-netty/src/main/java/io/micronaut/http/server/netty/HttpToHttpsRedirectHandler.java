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
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.netty.NettyHttpResponseBuilder;
import io.micronaut.http.server.util.HttpHostResolver;
import io.micronaut.http.ssl.ServerSslConfiguration;
import io.micronaut.http.uri.UriBuilder;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.ssl.SslHandler;

/**
 * Handler to automatically redirect HTTP to HTTPS request when using dual protocol.
 *
 * @author Iván López
 * @since 2.5.0
 */
@Internal
class HttpToHttpsRedirectHandler extends ChannelDuplexHandler {

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
        if (ctx.pipeline().get(SslHandler.class) == null && msg instanceof HttpRequest) {
            HttpRequest<?> request = (HttpRequest<?>) msg;
            UriBuilder uriBuilder = UriBuilder.of(hostResolver.resolve(request));
            uriBuilder.scheme("https");
            int port = sslConfiguration.getPort();
            if (port == 443) {
                uriBuilder.port(-1);
            } else {
                uriBuilder.port(port);
            }
            uriBuilder.path(request.getPath());

            MutableHttpResponse<?> response = HttpResponse
                    .permanentRedirect(uriBuilder.build())
                    .header(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            io.netty.handler.codec.http.HttpResponse nettyResponse = NettyHttpResponseBuilder.toHttpResponse(response);
            ctx.writeAndFlush(nettyResponse);
        } else {
            ctx.fireChannelRead(msg);
        }
    }
}
