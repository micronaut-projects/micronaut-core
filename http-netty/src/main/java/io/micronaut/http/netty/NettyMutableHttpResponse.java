/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delegates to Netty's {@link FullHttpResponse}.
 *
 * @param <B> The response body
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
@TypeHint(value = NettyMutableHttpResponse.class)
public class NettyMutableHttpResponse<B> implements MutableHttpResponse<B> {
    private static final ServerCookieEncoder DEFAULT_SERVER_COOKIE_ENCODER = ServerCookieEncoder.LAX;

    protected FullHttpResponse nettyResponse;
    final NettyHttpHeaders headers;
    private final ConversionService conversionService;
    private B body;
    private final Map<Class, Optional> convertedBodies = new LinkedHashMap<>(1);
    private MutableConvertibleValues<Object> attributes;

    private ServerCookieEncoder serverCookieEncoder = DEFAULT_SERVER_COOKIE_ENCODER;

    /**
     * @param nettyResponse     The {@link FullHttpResponse}
     * @param conversionService The conversion service
     */
    @SuppressWarnings("MagicNumber")
    public NettyMutableHttpResponse(FullHttpResponse nettyResponse, ConversionService conversionService) {
        this.nettyResponse = nettyResponse;
        this.headers = new NettyHttpHeaders(nettyResponse.headers(), conversionService);
        this.conversionService = conversionService;
    }

    /**
     * @param conversionService The conversion service
     */
    @SuppressWarnings("MagicNumber")
    public NettyMutableHttpResponse(ConversionService conversionService) {
        this.nettyResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        this.headers = new NettyHttpHeaders(nettyResponse.headers(), conversionService);
        this.conversionService = conversionService;
    }

    @Override
    public String toString() {
        HttpStatus status = getStatus();
        return status.getCode() + " " + status.getReason();
    }

    @Override
    public Optional<MediaType> getContentType() {
        Optional<MediaType> contentType = MutableHttpResponse.super.getContentType();
        if (contentType.isPresent()) {
            return contentType;
        } else {
            Optional<B> body = getBody();
            if (body.isPresent()) {
                return MediaType.fromType(body.get().getClass());
            }
        }
        return Optional.empty();
    }

    @Override
    public MutableHttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        MutableConvertibleValues<Object> attributes = this.attributes;
        if (attributes == null) {
            synchronized (this) { // double check
                attributes = this.attributes;
                if (attributes == null) {
                    attributes = new MutableConvertibleValuesMap<>(new ConcurrentHashMap<>(4));
                    this.attributes = attributes;
                }
            }
        }
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
            String value = serverCookieEncoder.encode(nettyCookie.getNettyCookie());
            headers.add(HttpHeaderNames.SET_COOKIE, value);
        } else {
            throw new IllegalArgumentException("Argument is not a Netty compatible Cookie");
        }
        return this;
    }

    @Override
    public MutableHttpResponse<B> cookies(Set<Cookie> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return this;
        }
        for (Cookie cookie: cookies) {
            cookie(cookie);
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
        return getBody(Argument.of(type));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> getBody(Argument<T> type) {
        return convertedBodies.computeIfAbsent(type.getType(), aClass -> getBody().flatMap(b -> {
            ArgumentConversionContext<T> context = ConversionContext.of(type);
            if (b instanceof ByteBuffer) {
                return conversionService.convert(((ByteBuffer) b).asNativeBuffer(), context);
            }
            return conversionService.convert(b, context);
        }));
    }

    @Override
    public MutableHttpResponse<B> status(HttpStatus status, CharSequence message) {
        message = message == null ? status.getReason() : message;
        nettyResponse.setStatus(new HttpResponseStatus(status.getCode(), message.toString()));
        return this;
    }

    /**
     * @return The Netty {@link FullHttpResponse}
     */
    public FullHttpResponse getNativeResponse() {
        return nettyResponse;
    }

    @Override
    public NettyMutableHttpResponse<B> body(B body) {
        if (this.body != body) {
            if (this.body instanceof ByteBuf) {
                ((ByteBuf) this.body).release();
            }
            this.body = body;
            if (body instanceof ByteBuf) {
                replace((ByteBuf) body);
            }
        }
        return this;
    }

    /**
     * @param body The body to replace
     * @return The current instance
     */
    public NettyMutableHttpResponse replace(ByteBuf body) {
        this.nettyResponse = this.nettyResponse.replace(body);
        this.headers.setNettyHeaders(this.nettyResponse.headers());
        return this;
    }

    /**
     *
     * @return Server cookie encoder
     */
    public ServerCookieEncoder getServerCookieEncoder() {
        return serverCookieEncoder;
    }

    /**
     *
     * @param serverCookieEncoder Server cookie encoder
     */
    public void setServerCookieEncoder(ServerCookieEncoder serverCookieEncoder) {
        this.serverCookieEncoder = serverCookieEncoder;
    }
}
