/*
 * Copyright 2017 original authors
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
package org.particleframework.http.server.netty;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.particleframework.core.annotation.Internal;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.value.MutableConvertibleValues;
import org.particleframework.core.convert.value.MutableConvertibleValuesMap;
import org.particleframework.core.type.Argument;
import org.particleframework.http.*;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.cookie.Cookie;
import org.particleframework.http.server.netty.cookies.NettyCookie;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delegates to Netty's {@link DefaultFullHttpResponse}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class NettyHttpResponse<B> implements MutableHttpResponse<B> {
    private static final AttributeKey<NettyHttpResponse> KEY = AttributeKey.valueOf(NettyHttpResponse.class.getSimpleName());

    protected FullHttpResponse nettyResponse;
    private final ConversionService conversionService;
    final NettyHttpRequestHeaders headers;
    private B body;
    private final Map<Class, Optional> convertedBodies = new LinkedHashMap<>(1);
    private final MutableConvertibleValues<Object> attributes;

    public NettyHttpResponse(DefaultFullHttpResponse nettyResponse, ConversionService conversionService) {
        this.nettyResponse = nettyResponse;
        this.headers = new NettyHttpRequestHeaders(nettyResponse.headers(), conversionService);
        this.attributes = new MutableConvertibleValuesMap<>(new ConcurrentHashMap<>(4), conversionService);
        this.conversionService = conversionService;
    }

    public NettyHttpResponse(ConversionService conversionService) {
        this.nettyResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        this.headers = new NettyHttpRequestHeaders(nettyResponse.headers(), conversionService);
        this.attributes = new MutableConvertibleValuesMap<>(new ConcurrentHashMap<>(4), conversionService);
        this.conversionService = conversionService;
    }


    @Override
    public MutableHttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        return attributes;
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.valueOf(nettyResponse.status().code());
    }

    @Override
    public MutableHttpResponse<B> cookie(Cookie cookie) {
        if (cookie instanceof NettyCookie) {
            NettyCookie nettyCookie = (NettyCookie) cookie;
            String value = ServerCookieEncoder.LAX.encode(nettyCookie.getNettyCookie());
            headers.add(HttpHeaderNames.SET_COOKIE, value);
        } else {
            throw new IllegalArgumentException("Argument is not a Netty compatible Cookie");
        }
        return this;
    }

    @Override
    public Optional<B> getBody() {
        return Optional.ofNullable(body);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T1> Optional<T1> getBody(Class<T1> type) {
        return convertedBodies.computeIfAbsent(type, aClass -> getBody().flatMap(b -> conversionService.convert(b, type)));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> getBody(Argument<T> type) {
        return convertedBodies.computeIfAbsent(type.getType(), aClass -> getBody().flatMap(b -> conversionService.convert(b, ConversionContext.of(type))));
    }

    @Override
    public MutableHttpResponse<B> status(HttpStatus status, CharSequence message) {
        message = message == null ? status.getReason() : message;
        nettyResponse.setStatus(new HttpResponseStatus(status.getCode(), message.toString()));
        return this;
    }

    public FullHttpResponse getNativeResponse() {
        return nettyResponse;
    }

    @Override
    public MutableHttpResponse<B> body(B body) {
        this.body = body;
        return this;
    }

    public NettyHttpResponse replace(ByteBuf body) {
        this.nettyResponse = this.nettyResponse.replace(body);
        return this;
    }

    /**
     * Lookup the response from the context
     *
     * @param request The context
     * @return The {@link NettyHttpResponse}
     */
    @Internal
    public static NettyHttpResponse getOrCreate(NettyHttpRequest<?> request) {
        return getOr(request, HttpResponse.ok());
    }

    /**
     * Lookup the response from the context
     *
     * @param request The context
     * @return The {@link NettyHttpResponse}
     */
    @Internal
    public static NettyHttpResponse getOr(NettyHttpRequest<?> request, HttpResponse<?> alternative) {
        Attribute<NettyHttpResponse> attr = request.attr(KEY);
        NettyHttpResponse nettyHttpResponse = attr.get();
        if(nettyHttpResponse == null) {
            nettyHttpResponse = (NettyHttpResponse)alternative;
            attr.set(nettyHttpResponse);
        }
        return nettyHttpResponse;
    }
    /**
     * Lookup the response from the request
     *
     * @param request The request
     * @return The {@link NettyHttpResponse}
     */
    @Internal
    public static Optional<NettyHttpResponse> get(NettyHttpRequest<?> request) {
        NettyHttpResponse nettyHttpResponse = request.attr(KEY).get();
        return Optional.ofNullable(nettyHttpResponse);
    }

    /**
     * Lookup the response from the request
     *
     * @param request The request
     * @return The {@link NettyHttpResponse}
     */
    @Internal
    public static Optional<NettyHttpResponse> set(NettyHttpRequest<?> request, HttpResponse<?> response) {
        request.attr(KEY).set((NettyHttpResponse) response);
        return Optional.ofNullable((NettyHttpResponse) response);
    }
}
