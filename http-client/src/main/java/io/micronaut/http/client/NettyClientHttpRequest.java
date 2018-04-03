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
package io.micronaut.http.client;

import com.typesafe.netty.http.DefaultStreamedHttpRequest;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpParameters;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.Cookies;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.micronaut.http.netty.NettyHttpParameters;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import org.reactivestreams.Publisher;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Optional;

/**
 * Default implementation of {@link MutableHttpRequest} for the {@link HttpClient}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class NettyClientHttpRequest<B> implements MutableHttpRequest<B> {

    private final NettyHttpHeaders headers = new NettyHttpHeaders();
    private final MutableConvertibleValues<Object> attributes = new MutableConvertibleValuesMap<>();
    private final io.micronaut.http.HttpMethod httpMethod;
    private final URI uri;
    private B body;
    private NettyHttpParameters httpParameters;

    NettyClientHttpRequest(io.micronaut.http.HttpMethod httpMethod, URI uri) {
        this.httpMethod = httpMethod;
        this.uri = uri;
    }

    NettyClientHttpRequest(io.micronaut.http.HttpMethod httpMethod, String uri) {
        this.httpMethod = httpMethod;
        this.uri = URI.create(uri);
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
    public MutableHttpRequest<B> cookie(Cookie cookie) {
        if (cookie instanceof NettyCookie) {
            NettyCookie nettyCookie = (NettyCookie) cookie;
            String value = ClientCookieEncoder.LAX.encode(nettyCookie.getNettyCookie());
            headers.add(HttpHeaderNames.COOKIE, value);
        } else {
            throw new IllegalArgumentException("Argument is not a Netty compatible Cookie");
        }
        return this;
    }

    @Override
    public Optional<B> getBody() {
        return Optional.ofNullable(body);
    }

    @Override
    public <T> Optional<T> getBody(Class<T> type) {
        return getBody(Argument.of(type));
    }

    @Override
    public <T> Optional<T> getBody(Argument<T> type) {
        return getBody().flatMap(b -> ConversionService.SHARED.convert(b, ConversionContext.of(type)));
    }

    @Override
    public MutableHttpRequest<B> body(B body) {
        this.body = body;
        return this;
    }

    @Override
    public Cookies getCookies() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public HttpParameters getParameters() {
        NettyHttpParameters httpParameters = this.httpParameters;
        if (httpParameters == null) {
            synchronized (this) { // double check
                httpParameters = this.httpParameters;
                if (httpParameters == null) {
                    this.httpParameters = httpParameters = decodeParameters(getUri().getRawPath());
                }
            }
        }
        return httpParameters;
    }

    @Override
    public io.micronaut.http.HttpMethod getMethod() {
        return httpMethod;
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public String getPath() {
        return uri.getPath();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return getServerAddress();
    }

    @Override
    public InetSocketAddress getServerAddress() {
        String host = uri.getHost();
        int port = uri.getPort();
        return new InetSocketAddress(host != null ? host : "localhost", port > -1 ? port : 80);
    }

    @Override
    public String getServerName() {
        return uri.getHost();
    }

    @Override
    public boolean isSecure() {
        String scheme = getUri().getScheme();
        return scheme != null && scheme.equals("https");
    }

    private NettyHttpParameters decodeParameters(String uri) {
        QueryStringDecoder queryStringDecoder = createDecoder(uri);
        return new NettyHttpParameters(queryStringDecoder.parameters(), ConversionService.SHARED);
    }

    protected QueryStringDecoder createDecoder(String uri) {
        Charset charset = getCharacterEncoding();
        return charset != null ? new QueryStringDecoder(uri, charset) : new QueryStringDecoder(uri);
    }

    HttpRequest getNettyRequest(Publisher<HttpContent> bodyPublisher) {
        HttpRequest request;
        if (bodyPublisher != null) {
            request = new DefaultStreamedHttpRequest(HttpVersion.HTTP_1_1, io.netty.handler.codec.http.HttpMethod.valueOf(httpMethod.name()), getUri().toString(), bodyPublisher);
        } else {
            request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, io.netty.handler.codec.http.HttpMethod.valueOf(httpMethod.name()), getUri().toString());
        }
        request.headers().setAll(headers.getNettyHeaders());
        return request;
    }

    HttpRequest getNettyRequest(ByteBuf content) {
        String uriStr = resolveUriPath();
        io.netty.handler.codec.http.HttpMethod method = io.netty.handler.codec.http.HttpMethod.valueOf(httpMethod.name());
        DefaultFullHttpRequest req = content != null ? new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uriStr, content) :
            new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uriStr);
        req.headers().setAll(headers.getNettyHeaders());
        return req;
    }

    private String resolveUriPath() {
        URI uri = getUri();
        if (StringUtils.isNotEmpty(uri.getScheme())) {
            try {
                // obtain just the path
                uri = new URI(null, null, null, -1, uri.getPath(), uri.getQuery(), uri.getFragment());
            } catch (URISyntaxException e) {
                // ignore
            }
        }
        return uri.toString();
    }
}
