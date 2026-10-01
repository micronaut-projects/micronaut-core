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
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
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
import org.jspecify.annotations.Nullable;

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

    /**
     * The query component of a request target, verbatim, or {@code null} if it has none. An empty
     * string means the target ended in {@code ?}.
     */
    @Nullable
    static String rawQuery(String target) {
        int query = target.indexOf('?');
        if (query < 0) {
            return null;
        }
        int fragment = target.indexOf('#', query);
        return fragment < 0 ? target.substring(query + 1) : target.substring(query + 1, fragment);
    }

    @Override
    public void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
        NettyHttpRequest<?> strippedRequest = new NettyHttpRequest<>(request, body, ctx, conversionService, serverConfiguration);

        // read everything that is needed off the request before releasing it
        String host = hostResolver.resolve(strippedRequest);
        String path = strippedRequest.getPath();
        // Taken from the request target as received rather than from getUri(): for an
        // absolute-form target (GET http://host/p?q HTTP/1.1, as a proxy may send) the request
        // URI is rebuilt from decoded components, which turns a percent-encoded reserved character
        // such as %26 back into a bare & and changes the structure of the query.
        String rawQuery = rawQuery(request.uri());
        strippedRequest.release();

        UriBuilder uriBuilder = UriBuilder.of(host);
        uriBuilder.scheme("https");
        int port = sslConfiguration.getPort();
        if (port == 443) {
            uriBuilder.port(-1);
        } else {
            uriBuilder.port(port);
        }
        uriBuilder.path(path);

        StringBuilder location = new StringBuilder(uriBuilder.build().toASCIIString());
        // a null raw query is the only case that means the request target had no query component. An
        // empty one means it ended in '?', which is a distinct URI form and is reproduced as such.
        // UriBuilder only models decoded query parameters, so the query string is carried over verbatim
        // and the header value is assembled directly rather than round-tripped through URI again.
        if (rawQuery != null) {
            location.append('?').append(rawQuery);
        }

        outboundAccess.closeAfterWrite();
        outboundAccess.write(
            NettyHttpResponseBuilder.toHttpResponse(
                HttpResponse.status(HttpStatus.PERMANENT_REDIRECT).header(HttpHeaders.LOCATION, location.toString())),
                NettyByteBodyFactory.empty()
        );
    }

    @Override
    public void handleUnboundError(Throwable cause) {
        // this connection doesn't process requests, so just ignore errors
    }
}
