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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.exceptions.UriSyntaxException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

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

    private static final Logger LOG = LoggerFactory.getLogger(HttpToHttpsRedirectHandler.class);

    @Override
    public void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
        URI location;
        try {
            location = location(ctx, request, body);
        } catch (IllegalArgumentException | UriSyntaxException e) {
            // the request target or the host it resolves to is not a valid URI (for example a
            // query containing characters java.net.URI rejects). No redirect can be built, so
            // answer with an empty 400 rather than leaving the request unanswered. The regular
            // request path also answers such a request with a 400, through its error handling.
            LOG.debug("Cannot redirect a request with an invalid URI or host to HTTPS", e);
            outboundAccess.closeAfterWrite();
            outboundAccess.write(
                NettyHttpResponseBuilder.toHttpResponse(HttpResponse.status(HttpStatus.BAD_REQUEST)),
                NettyByteBodyFactory.empty()
            );
            return;
        }

        outboundAccess.closeAfterWrite();
        outboundAccess.write(
            NettyHttpResponseBuilder.toHttpResponse(HttpResponse.permanentRedirect(location)),
                NettyByteBodyFactory.empty()
        );
    }

    /**
     * Builds the redirect location for the given request, and releases the body.
     *
     * @throws IllegalArgumentException if the request target is not a valid URI
     * @throws UriSyntaxException if the resolved host is not a valid URI authority
     */
    private URI location(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body) {
        NettyHttpRequest<?> strippedRequest;
        try {
            strippedRequest = new NettyHttpRequest<>(request, body, ctx, conversionService, serverConfiguration);
        } catch (IllegalArgumentException e) {
            body.close();
            throw e;
        }

        String host;
        String path;
        try {
            host = hostResolver.resolve(strippedRequest);
            path = strippedRequest.getPath();
        } finally {
            strippedRequest.release();
        }

        UriBuilder uriBuilder = UriBuilder.of(host);
        uriBuilder.scheme("https");
        int port = sslConfiguration.getPort();
        if (port == 443) {
            uriBuilder.port(-1);
        } else {
            uriBuilder.port(port);
        }
        uriBuilder.path(path);
        return uriBuilder.build();
    }

    @Override
    public void handleUnboundError(Throwable cause) {
        // this connection doesn't process requests, so just ignore errors
    }
}
