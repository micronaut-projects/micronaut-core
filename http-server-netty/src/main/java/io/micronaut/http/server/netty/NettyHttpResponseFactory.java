/*
 * Copyright 2017-2020 original authors
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
import io.micronaut.http.HttpResponseFactory;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.Attribute;

import java.util.Optional;

/**
 * Implementation of {@link HttpResponseFactory} for Netty.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class NettyHttpResponseFactory implements HttpResponseFactory {

    @Override
    public <T> MutableHttpResponse<T> ok(T body) {
        return new NettyMutableHttpResponse<>(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, null, body, ConversionService.SHARED);
    }

    @Override
    public <T> MutableHttpResponse<T> status(HttpStatus status, T body) {
        return ok(body).status(status);
    }

    @Override
    public MutableHttpResponse status(HttpStatus status, String reason) {
        HttpResponseStatus nettyStatus;
        if (reason == null) {
            nettyStatus = HttpResponseStatus.valueOf(status.getCode());
        } else {
            nettyStatus = HttpResponseStatus.valueOf(status.getCode(), reason);
        }

        return new NettyMutableHttpResponse(HttpVersion.HTTP_1_1, nettyStatus, ConversionService.SHARED);
    }

    @Override
    public <T> MutableHttpResponse<T> status(int status, String reason) {
        HttpResponseStatus nettyStatus;
        if (reason == null) {
            nettyStatus = HttpResponseStatus.valueOf(status);
        } else {
            nettyStatus = HttpResponseStatus.valueOf(status, reason);
        }

        return new NettyMutableHttpResponse(HttpVersion.HTTP_1_1, nettyStatus, ConversionService.SHARED);
    }

    /**
     * Lookup the response from the context.
     *
     * @param request The context
     * @return The {@link NettyMutableHttpResponse}
     */
    @Internal
    public static NettyMutableHttpResponse getOrCreate(NettyHttpRequest<?> request) {
        return getOr(request, io.micronaut.http.HttpResponse.ok());
    }

    /**
     * Lookup the response from the context.
     *
     * @param request     The context
     * @param alternative The alternative HttpResponse
     * @return The {@link NettyMutableHttpResponse}
     */
    @Internal
    public static NettyMutableHttpResponse getOr(NettyHttpRequest<?> request, io.micronaut.http.HttpResponse<?> alternative) {
        Attribute<NettyMutableHttpResponse> attr = request.attr(ServerAttributeKeys.RESPONSE_KEY);
        NettyMutableHttpResponse nettyHttpResponse = attr.get();
        if (nettyHttpResponse == null) {
            nettyHttpResponse = (NettyMutableHttpResponse) alternative;
            attr.set(nettyHttpResponse);
        }
        return nettyHttpResponse;
    }

    /**
     * Lookup the response from the request.
     *
     * @param request The request
     * @return The {@link NettyMutableHttpResponse}
     */
    @Internal
    public static Optional<NettyMutableHttpResponse> get(NettyHttpRequest<?> request) {
        NettyMutableHttpResponse nettyHttpResponse = request.attr(ServerAttributeKeys.RESPONSE_KEY).get();
        return Optional.ofNullable(nettyHttpResponse);
    }

    /**
     * Lookup the response from the request.
     *
     * @param request  The request
     * @param response The Http Response
     * @return The {@link NettyMutableHttpResponse}
     */
    @Internal
    public static Optional<NettyMutableHttpResponse> set(NettyHttpRequest<?> request, HttpResponse<?> response) {
        request.attr(ServerAttributeKeys.RESPONSE_KEY).set((NettyMutableHttpResponse) response);
        return Optional.ofNullable((NettyMutableHttpResponse) response);
    }
}
