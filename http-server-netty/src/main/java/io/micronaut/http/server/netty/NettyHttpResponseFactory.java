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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseFactory;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.netty.NettyHttpResponse;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.util.Optional;

/**
 * Implementation of {@link HttpResponseFactory} for Netty
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class NettyHttpResponseFactory implements HttpResponseFactory {

    private static final AttributeKey<NettyHttpResponse> KEY = AttributeKey.valueOf(NettyHttpResponse.class.getSimpleName());

    @Override
    public <T> MutableHttpResponse<T> ok(T body) {
        MutableHttpResponse<T> ok = new NettyHttpResponse<>(ConversionService.SHARED);

        return body != null ? ok.body(body) : ok;
    }

    @Override
    public <T> MutableHttpResponse<T> status(HttpStatus status, T body) {
        MutableHttpResponse<T> ok = new NettyHttpResponse<>(ConversionService.SHARED);
        ok.status(status);
        return body != null ? ok.body(body) : ok;
    }

    @Override
    public MutableHttpResponse status(HttpStatus status, String reason) {
        HttpResponseStatus nettyStatus;
        if (reason == null) {
            nettyStatus = HttpResponseStatus.valueOf(status.getCode());
        } else {
            nettyStatus = new HttpResponseStatus(status.getCode(), reason);
        }

        DefaultFullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, nettyStatus);
        return new NettyHttpResponse(fullHttpResponse, ConversionService.SHARED);
    }

    /**
     * Lookup the response from the context
     *
     * @param request The context
     * @return The {@link io.micronaut.http.netty.NettyHttpResponse}
     */
    @Internal
    public static io.micronaut.http.netty.NettyHttpResponse getOrCreate(NettyHttpRequest<?> request) {
        return getOr(request, io.micronaut.http.HttpResponse.ok());
    }

    /**
     * Lookup the response from the context
     *
     * @param request The context
     * @return The {@link io.micronaut.http.netty.NettyHttpResponse}
     */
    @Internal
    public static io.micronaut.http.netty.NettyHttpResponse getOr(NettyHttpRequest<?> request, io.micronaut.http.HttpResponse<?> alternative) {
        Attribute<io.micronaut.http.netty.NettyHttpResponse> attr = request.attr(KEY);
        io.micronaut.http.netty.NettyHttpResponse nettyHttpResponse = attr.get();
        if (nettyHttpResponse == null) {
            nettyHttpResponse = (io.micronaut.http.netty.NettyHttpResponse) alternative;
            attr.set(nettyHttpResponse);
        }
        return nettyHttpResponse;
    }

    /**
     * Lookup the response from the request
     *
     * @param request The request
     * @return The {@link io.micronaut.http.netty.NettyHttpResponse}
     */
    @Internal
    public static Optional<io.micronaut.http.netty.NettyHttpResponse> get(NettyHttpRequest<?> request) {
        io.micronaut.http.netty.NettyHttpResponse nettyHttpResponse = request.attr(KEY).get();
        return Optional.ofNullable(nettyHttpResponse);
    }

    /**
     * Lookup the response from the request
     *
     * @param request The request
     * @return The {@link io.micronaut.http.netty.NettyHttpResponse}
     */
    @Internal
    public static Optional<io.micronaut.http.netty.NettyHttpResponse> set(NettyHttpRequest<?> request, HttpResponse<?> response) {
        request.attr(KEY).set((io.micronaut.http.netty.NettyHttpResponse) response);
        return Optional.ofNullable((io.micronaut.http.netty.NettyHttpResponse) response);
    }
}
