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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpParameters;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.netty.NettyHttpParameters;
import io.micronaut.http.netty.NettyHttpRequestBuilder;
import io.micronaut.http.netty.stream.DefaultStreamedHttpRequest;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.web.router.uri.UriUtil;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.DefaultAttributeMap;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;

/**
 * Abstract implementation of {@link HttpRequest} for Netty.
 *
 * @param <B> The body
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public abstract class AbstractNettyHttpRequest<B> extends DefaultAttributeMap implements HttpRequest<B>, NettyHttpRequestBuilder {

    protected final io.netty.handler.codec.http.HttpRequest nettyRequest;
    protected final ConversionService conversionService;
    protected final HttpMethod httpMethod;
    protected final String unvalidatedUrl;
    protected final String httpMethodName;

    private volatile URI uri;
    private volatile NettyHttpParameters httpParameters;
    private volatile Charset charset;
    private volatile String path;

    /**
     * @param nettyRequest      The Http netty request
     * @param conversionService The conversion service
     * @param escapeHtmlUrl     {@link HttpServerConfiguration#isEscapeHtmlUrl()}
     */
    public AbstractNettyHttpRequest(io.netty.handler.codec.http.HttpRequest nettyRequest, ConversionService conversionService, boolean escapeHtmlUrl) {
        this.nettyRequest = nettyRequest;
        this.conversionService = conversionService;
        String uri = nettyRequest.uri();
        if (!UriUtil.isValidPath(uri)) {
            if (escapeHtmlUrl && UriUtil.isRelative(uri)) {
                uri = UriUtil.toValidPath(uri);
            }
            this.uri = createURI(uri);
        }
        this.unvalidatedUrl = uri;
        this.httpMethodName = nettyRequest.method().name();
        this.httpMethod = HttpMethod.parse(httpMethodName);
    }

    @NonNull
    @Override
    public io.netty.handler.codec.http.HttpRequest toHttpRequest() {
        return this.nettyRequest;
    }

    @NonNull
    @Override
    public io.netty.handler.codec.http.FullHttpRequest toFullHttpRequest() {
        if (this.nettyRequest instanceof io.netty.handler.codec.http.FullHttpRequest request) {
            return request;
        }
        DefaultFullHttpRequest httpRequest = new DefaultFullHttpRequest(
                this.nettyRequest.protocolVersion(),
                this.nettyRequest.method(),
                this.nettyRequest.uri()
        );
        httpRequest.headers().setAll(this.nettyRequest.headers());
        return httpRequest;
    }

    @NonNull
    @Override
    public StreamedHttpRequest toStreamHttpRequest() {
        if (isStream()) {
            return (StreamedHttpRequest) this.nettyRequest;
        } else {
            if (this.nettyRequest instanceof io.netty.handler.codec.http.FullHttpRequest request) {

                return new DefaultStreamedHttpRequest(
                        io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                        this.nettyRequest.method(),
                        this.nettyRequest.uri(),
                        true,
                        Publishers.just(new DefaultLastHttpContent(request.content()))
                        );
            } else {
                return new DefaultStreamedHttpRequest(
                        io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                        this.nettyRequest.method(),
                        this.nettyRequest.uri(),
                        true,
                        Publishers.just(LastHttpContent.EMPTY_LAST_CONTENT)
                );
            }
        }
    }

    @Override
    public boolean isStream() {
        return this.nettyRequest instanceof StreamedHttpRequest;
    }

    /**
     * @return The native netty request
     */
    public io.netty.handler.codec.http.HttpRequest getNettyRequest() {
        return nettyRequest;
    }

    @Override
    public HttpParameters getParameters() {
        NettyHttpParameters params = this.httpParameters;
        if (params == null) {
            synchronized (this) { // double check
                params = this.httpParameters;
                if (params == null) {
                    params = decodeParameters();
                    this.httpParameters = params;
                }
            }
        }
        return params;
    }

    @Override
    public Charset getCharacterEncoding() {
        if (charset == null) {
            charset = initCharset(HttpRequest.super.getCharacterEncoding());
        }
        return charset;
    }

    @Override
    public HttpMethod getMethod() {
        return httpMethod;
    }

    @Override
    public URI getUri() {
        URI u = this.uri;
        if (u == null) {
            u = createURI(unvalidatedUrl);
            this.uri = u;
        }
        return u;
    }

    @Override
    public String getPath() {
        String p = this.path;
        if (p == null) {
            p = parsePath(unvalidatedUrl);
            this.path = p;
        }
        return p;
    }

    /**
     * @param characterEncoding The character encoding
     * @return The Charset
     */
    protected abstract Charset initCharset(Charset characterEncoding);

    /**
     * @return the maximum number of parameters.
     */
    protected abstract int getMaxParams();

    /**
     * @return {@code true} if yes, {@code false} otherwise.
     */
    protected abstract boolean isSemicolonIsNormalChar();

    /**
     * @param uri The URI
     * @return The query string decoder
     */
    @SuppressWarnings("ConstantConditions")
    protected final QueryStringDecoder createDecoder(URI uri) {
        Charset cs = getCharacterEncoding();
        boolean semicolonIsNormalChar = isSemicolonIsNormalChar();
        int maxParams = getMaxParams();
        return cs != null ?
            new QueryStringDecoder(uri, cs, maxParams, semicolonIsNormalChar) :
            new QueryStringDecoder(uri, HttpConstants.DEFAULT_CHARSET, maxParams, semicolonIsNormalChar);
    }

    private NettyHttpParameters decodeParameters() {
        QueryStringDecoder queryStringDecoder = createDecoder(getUri());
        return new NettyHttpParameters(queryStringDecoder.parameters(), conversionService, null);
    }

    @Override
    public String getMethodName() {
        return httpMethodName;
    }

    private static URI createURI(String url) {
        URI fullUri = URI.create(url);
        if (fullUri.getAuthority() != null || fullUri.getScheme() != null) {
            // https://example.com/foo -> /foo
            try {
                fullUri = new URI(
                    null, // scheme
                    null, // authority
                    fullUri.getPath(),
                    fullUri.getQuery(),
                    fullUri.getFragment()
                );
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
        }
        return fullUri;
    }

    /**
     * Extract the path out of the uri.
     * https://github.com/eclipse-vertx/vert.x/blob/master/src/main/java/io/vertx/core/http/impl/HttpUtils.java
     */
    private static String parsePath(String uri) {
        if (uri.isEmpty()) {
            return "";
        }
        int i;
        if (uri.charAt(0) == '/') {
            i = 0;
        } else {
            i = uri.indexOf("://");
            if (i == -1) {
                i = 0;
            } else {
                i = uri.indexOf('/', i + 3);
                if (i == -1) {
                    // contains no /
                    return "/";
                }
            }
        }
        int queryStart = uri.indexOf('?', i);
        if (queryStart == -1) {
            queryStart = uri.length();
            if (i == 0) {
                return uri;
            }
        }
        return uri.substring(i, queryStart);
    }
}
