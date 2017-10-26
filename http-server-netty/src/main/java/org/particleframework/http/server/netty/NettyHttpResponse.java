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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.value.MutableConvertibleValues;
import org.particleframework.core.convert.value.MutableConvertibleValuesMap;
import org.particleframework.http.*;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.cookie.Cookie;
import org.particleframework.http.server.netty.cookies.NettyCookies;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Delegates to Netty's {@link DefaultFullHttpResponse}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class NettyHttpResponse<B> implements MutableHttpResponse<B> {
    private static final String KEY = "$particle.response";

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
        if (cookie instanceof NettyCookies.NettyCookie) {
            NettyCookies.NettyCookie nettyCookie = (NettyCookies.NettyCookie) cookie;
            String value = ServerCookieEncoder.LAX.encode(nettyCookie.getNettyCookie());
            headers.add(HttpHeaderNames.SET_COOKIE, value);
        } else {
            throw new IllegalArgumentException("Argument is not a Netty compatible Cookie");
        }
        return this;
    }

    @Override
    public B getBody() {
        return body;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T1> Optional<T1> getBody(Class<T1> type) {
        return convertedBodies.computeIfAbsent(type, aClass -> {
            B body = getBody();
            if(body != null) {
                return conversionService.convert(body, aClass);
            }
            return Optional.empty();

        });
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
    public static NettyHttpResponse getOrCreate(NettyHttpRequest<?> request) {
        return request.getAttributes().get(KEY, NettyHttpResponse.class).orElse((NettyHttpResponse)HttpResponse.ok());
    }

    /**
     * Lookup the response from the context
     *
     * @param request The context
     * @return The {@link NettyHttpResponse}
     */
    public static NettyHttpResponse getOr(NettyHttpRequest<?> request, HttpResponse<?> alternative) {
        return request.getAttributes().get(KEY, NettyHttpResponse.class).orElseGet(() -> {
            request.getAttributes().put(KEY, alternative);
            return (NettyHttpResponse) alternative;
        });
    }
    /**
     * Lookup the response from the request
     *
     * @param request The request
     * @return The {@link NettyHttpResponse}
     */
    public static Optional<NettyHttpResponse> get(NettyHttpRequest<?> request) {
        return request.getAttributes().get(KEY, NettyHttpResponse.class);
    }

    /**
     * Lookup the response from the request
     *
     * @param request The request
     * @return The {@link NettyHttpResponse}
     */
    public static Optional<NettyHttpResponse> set(org.particleframework.http.HttpRequest<?> request, HttpResponse<?> response) {
        MutableConvertibleValues<Object> attributes = request.getAttributes();
        if(response == null) {
            attributes.remove(KEY);
        }
        else {
            attributes.put(KEY, response);
        }
        return Optional.ofNullable((NettyHttpResponse) response);
    }
}
