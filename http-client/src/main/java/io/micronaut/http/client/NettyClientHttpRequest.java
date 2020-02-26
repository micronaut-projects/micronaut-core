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
package io.micronaut.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpParameters;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.Cookies;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.micronaut.http.netty.NettyHttpParameters;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.http.netty.stream.DefaultStreamedHttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import org.reactivestreams.Publisher;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Default implementation of {@link MutableHttpRequest} for the {@link HttpClient}.
 *
 * @param <B> The request body
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class NettyClientHttpRequest<B> implements MutableHttpRequest<B> {

    private final NettyHttpHeaders headers = new NettyHttpHeaders();
    private final MutableConvertibleValues<Object> attributes = new MutableConvertibleValuesMap<>();
    private final HttpMethod httpMethod;
    private final String httpMethodName;
    private URI uri;
    private B body;
    private NettyHttpParameters httpParameters;

    /**
     * @param httpMethod The Http method
     * @param uri        The URI
     */
    NettyClientHttpRequest(HttpMethod httpMethod, URI uri) {
        this(httpMethod, uri, httpMethod.name());
    }

    /**
     * This constructor is actually required for the case of non-standard http methods.
     *
     * @param httpMethod     The http method. CUSTOM value is used for non-standard
     * @param uri            The uri
     * @param httpMethodName Method name. Is the same as httpMethod.name() value for standard http methods.
     */
    NettyClientHttpRequest(HttpMethod httpMethod, URI uri, String httpMethodName) {
        this.httpMethod = httpMethod;
        this.uri = uri;
        this.httpMethodName = httpMethodName;
    }

    /**
     * @param httpMethod The Http method
     * @param uri        The URI
     */
    NettyClientHttpRequest(HttpMethod httpMethod, String uri) {
        this(httpMethod, uri, httpMethod.name());
    }

    /**
     * This constructor is actually required for the case of non-standard http methods.
     *
     * @param httpMethod     The http method. CUSTOM value is used for non-standard
     * @param uri            The uri
     * @param httpMethodName Method name. Is the same as httpMethod.name() value for standard http methods.
     */
    NettyClientHttpRequest(HttpMethod httpMethod, String uri, String httpMethodName) {
        this(httpMethod, URI.create(uri), httpMethodName);
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
    public MutableHttpRequest<B> cookies(Set<Cookie> cookies) {
        if (cookies.size() > 1) {
            Set<String> values = new HashSet<>(cookies.size());
            for (Cookie cookie: cookies) {
                if (cookie instanceof NettyCookie) {
                    NettyCookie nettyCookie = (NettyCookie) cookie;
                    String value = ClientCookieEncoder.LAX.encode(nettyCookie.getNettyCookie());
                    values.add(value);
                } else {
                    throw new IllegalArgumentException("Argument is not a Netty compatible Cookie");
                }
            }
            headers.add(HttpHeaderNames.COOKIE, String.join(";", values));
        } else if (!cookies.isEmpty()) {
            cookie(cookies.iterator().next());
        }
        return this;
    }

    @Override
    public MutableHttpRequest<B> uri(URI uri) {
        this.uri = uri;
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
    public MutableHttpParameters getParameters() {
        NettyHttpParameters httpParameters = this.httpParameters;
        if (httpParameters == null) {
            synchronized (this) { // double check
                httpParameters = this.httpParameters;
                if (httpParameters == null) {
                    httpParameters = decodeParameters(getUri().getRawPath());
                    this.httpParameters = httpParameters;
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

    private NettyHttpParameters decodeParameters(String uri) {
        QueryStringDecoder queryStringDecoder = createDecoder(uri);
        return new NettyHttpParameters(queryStringDecoder.parameters(), ConversionService.SHARED);
    }

    /**
     * @param uri The URI
     * @return The query string decoder
     */
    protected QueryStringDecoder createDecoder(String uri) {
        Charset charset = getCharacterEncoding();
        return charset != null ? new QueryStringDecoder(uri, charset) : new QueryStringDecoder(uri);
    }

    /**
     * @param content The {@link ByteBuf}
     * @return The http request
     */
    HttpRequest getFullRequest(ByteBuf content) {
        String uriStr = resolveUriPath();
        io.netty.handler.codec.http.HttpMethod method = getMethod(httpMethodName);
        DefaultFullHttpRequest req = content != null ? new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uriStr, content) :
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uriStr);
        req.headers().set(headers.getNettyHeaders());
        return req;
    }

    private static io.netty.handler.codec.http.HttpMethod getMethod(String httpMethodName) {
        return io.netty.handler.codec.http.HttpMethod.valueOf(httpMethodName);
    }

    /**
     * @param publisher A publisher for the Http content
     * @return The Http request
     */
    HttpRequest getStreamedRequest(Publisher<HttpContent> publisher) {
        String uriStr = resolveUriPath();
        io.netty.handler.codec.http.HttpMethod method = getMethod(httpMethodName);
        HttpRequest req = publisher != null ? new DefaultStreamedHttpRequest(HttpVersion.HTTP_1_1, method, uriStr, publisher) :
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uriStr);
        req.headers().set(headers.getNettyHeaders());
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

    @Override
    public String toString() {
        return getMethodName() + " " + uri;
    }

    @Override
    public String getMethodName() {
        return httpMethodName;
    }
}
