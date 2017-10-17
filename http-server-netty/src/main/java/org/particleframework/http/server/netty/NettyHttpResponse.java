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
import org.particleframework.http.HttpResponse;
import org.particleframework.http.HttpStatus;
import org.particleframework.http.MutableHttpHeaders;
import org.particleframework.http.MutableHttpResponse;
import org.particleframework.http.cookie.Cookie;
import org.particleframework.http.server.netty.cookies.NettyCookies;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Delegates to Netty's {@link DefaultFullHttpResponse}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class NettyHttpResponse<B> implements MutableHttpResponse<B> {
    public static final AttributeKey<NettyHttpResponse> KEY = AttributeKey.valueOf(NettyHttpResponse.class.getSimpleName());

    protected FullHttpResponse nettyResponse;
    private final ConversionService conversionService;
    final NettyHttpRequestHeaders headers;
    private B body;
    private final Map<Class, Optional> convertedBodies = new LinkedHashMap<>(1);

    public NettyHttpResponse(DefaultFullHttpResponse nettyResponse, ConversionService conversionService) {
        this.nettyResponse = nettyResponse;
        this.headers = new NettyHttpRequestHeaders(nettyResponse.headers(), conversionService);
        this.conversionService = conversionService;
    }

    public NettyHttpResponse(ConversionService conversionService) {
        this.nettyResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        this.headers = new NettyHttpRequestHeaders(nettyResponse.headers(), conversionService);
        this.conversionService = conversionService;
    }


    @Override
    public MutableHttpHeaders getHeaders() {
        return headers;
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
    public MutableHttpResponse<B> setBody(B body) {
        this.body = body;
        return this;
    }

    /**
     * Lookup the response from the context
     *
     * @param ctx The response
     * @return
     */
    public static NettyHttpResponse getOrCreate(ChannelHandlerContext ctx) {
        Attribute<NettyHttpResponse> attr = ctx
                .channel()
                .attr(KEY);
        NettyHttpResponse nettyHttpResponse = attr.get();
        if(nettyHttpResponse == null) {
            nettyHttpResponse = (NettyHttpResponse)HttpResponse.ok();
            attr.set(nettyHttpResponse);
        }
        return nettyHttpResponse;
    }

    public NettyHttpResponse replace(ByteBuf body) {
        this.nettyResponse = this.nettyResponse.replace(body);
        return this;
    }
}
