/*
 * Copyright 2018 original authors
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
package org.particleframework.http.client;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.value.MutableConvertibleValues;
import org.particleframework.core.convert.value.MutableConvertibleValuesMap;
import org.particleframework.core.type.Argument;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.MutableHttpHeaders;
import org.particleframework.http.MutableHttpRequest;
import org.particleframework.http.cookie.Cookies;
import org.particleframework.http.netty.AbstractNettyHttpRequest;
import org.particleframework.http.netty.NettyHttpHeaders;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class NettyClientHttpRequest<B> extends AbstractNettyHttpRequest<B> implements MutableHttpRequest<B>{

    private final NettyHttpHeaders headers;
    private final MutableConvertibleValues<Object> attributes;
    private B body;

    NettyClientHttpRequest(HttpMethod httpMethod, String uri) {
        super(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, io.netty.handler.codec.http.HttpMethod.valueOf(httpMethod.name()), uri), ConversionService.SHARED);
        this.headers = new NettyHttpHeaders(nettyRequest.headers(), ConversionService.SHARED);
        this.attributes = new MutableConvertibleValuesMap<>();
    }


    @Override
    protected Charset initCharset(Charset characterEncoding) {
        return characterEncoding != null ? characterEncoding : StandardCharsets.UTF_8;
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
    public Optional<B> getBody() {
        return Optional.ofNullable(body);
    }

    @Override
    public <T> Optional<T> getBody(Class<T> type) {
        if(body != null) {
            return ConversionService.SHARED.convert(body, type);
        }
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getBody(Argument<T> type) {
        if(body != null) {
            return ConversionService.SHARED.convert(body, ConversionContext.of(type));
        }
        return Optional.empty();
    }

    @Override
    public MutableHttpRequest<B> body(B body) {
        this.body = body;
        return this;
    }

    @Override
    public Cookies getCookies() {
        return null;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return null;
    }

    @Override
    public InetSocketAddress getServerAddress() {
        return null;
    }

    @Override
    public String getServerName() {
        return null;
    }

    @Override
    public boolean isSecure() {
        return false;
    }
}
