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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.netty.NettyHttpResponseBuilder;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.handler.OutboundAccess;
import io.micronaut.http.server.netty.handler.RequestHandler;
import io.micronaut.http.server.util.HttpHostResolver;
import io.micronaut.http.ssl.ServerSslConfiguration;
import io.micronaut.http.uri.UriBuilder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

/**
 * Handler to automatically redirect HTTP to HTTPS request when using dual protocol.
 *
 * @param conversionService   The conversion service
 * @param serverConfiguration The server configuration
 * @param sslConfiguration    The SSL configuration
 * @param hostResolver        The host resolver
 * @author Iván López
 * @since 2.5.0
 */
@Internal
record HttpToHttpsRedirectHandler(
    ConversionService conversionService,
    NettyHttpServerConfiguration serverConfiguration,
    ServerSslConfiguration sslConfiguration,
    HttpHostResolver hostResolver
) implements RequestHandler {

    @Override
    public void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
        NettyHttpRequest<?> strippedRequest = new NettyHttpRequest<>(request, body, ctx, conversionService, serverConfiguration);

        UriBuilder uriBuilder = UriBuilder.of(hostResolver.resolve(strippedRequest));
        strippedRequest.release();
        uriBuilder.scheme("https");
        int port = sslConfiguration.getPort();
        if (port == 443) {
            uriBuilder.port(-1);
        } else {
            uriBuilder.port(port);
        }
        uriBuilder.path(strippedRequest.getPath());

        outboundAccess.closeAfterWrite();
        outboundAccess.write(
            NettyHttpResponseBuilder.toHttpResponse(HttpResponse.permanentRedirect(uriBuilder.build())),
                NettyByteBodyFactory.empty()
        );
    }

    @Override
    public void handleUnboundError(Throwable cause) {
        // this connection doesn't process requests, so just ignore errors
    }
}
