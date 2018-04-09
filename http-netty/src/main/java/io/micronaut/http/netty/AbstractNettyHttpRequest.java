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
package io.micronaut.http.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpParameters;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.DefaultAttributeMap;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Optional;

/**
 * Abstract implementation of {@link HttpRequest} for Netty
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public abstract class AbstractNettyHttpRequest<B> extends DefaultAttributeMap implements HttpRequest<B> {

    protected final io.netty.handler.codec.http.HttpRequest nettyRequest;
    protected final ConversionService<?> conversionService;
    protected final HttpMethod httpMethod;
    protected final URI uri;

    private NettyHttpParameters httpParameters;
    private MediaType mediaType;
    private Charset charset;
    private Locale locale;
    private String path;

    public AbstractNettyHttpRequest(io.netty.handler.codec.http.HttpRequest nettyRequest, ConversionService conversionService) {
        this.nettyRequest = nettyRequest;
        this.conversionService = conversionService;
        String fullUri = nettyRequest.uri();
        this.uri = URI.create(fullUri);
        this.httpMethod = HttpMethod.valueOf(nettyRequest.method().name());
    }

    /**
     * @return The native netty request
     */
    public io.netty.handler.codec.http.HttpRequest getNettyRequest() {
        return nettyRequest;
    }

    @Override
    public HttpParameters getParameters() {
        NettyHttpParameters httpParameters = this.httpParameters;
        if (httpParameters == null) {
            synchronized (this) { // double check
                httpParameters = this.httpParameters;
                if (httpParameters == null) {
                    this.httpParameters = httpParameters = decodeParameters(nettyRequest.uri());
                }
            }
        }
        return httpParameters;
    }

    @Override
    public Optional<MediaType> getContentType() {
        MediaType contentType = this.mediaType;
        if (contentType == null) {
            synchronized (this) { // double check
                contentType = this.mediaType;
                if (contentType == null) {
                    this.mediaType = contentType = HttpRequest.super.getContentType().orElse(null);
                }
            }
        }
        return Optional.ofNullable(contentType);
    }

    @Override
    public Charset getCharacterEncoding() {
        Charset charset = this.charset;
        if (charset == null) {
            synchronized (this) { // double check
                charset = this.charset;
                if (charset == null) {
                    this.charset = charset = initCharset(HttpRequest.super.getCharacterEncoding());
                }
            }
        }
        return charset;
    }

    @Override
    public Optional<Locale> getLocale() {
        Locale locale = this.locale;
        if (locale == null) {
            synchronized (this) { // double check
                locale = this.locale;
                if (locale == null) {
                    this.locale = locale = HttpRequest.super.getLocale().orElse(null);
                }
            }
        }
        return Optional.ofNullable(locale);
    }

    @Override
    public HttpMethod getMethod() {
        return httpMethod;
    }

    @Override
    public URI getUri() {
        return this.uri;
    }

    @Override
    public String getPath() {
        String path = this.path;
        if (path == null) {
            synchronized (this) { // double check
                path = this.path;
                if (path == null) {
                    this.path = path = decodePath(nettyRequest.uri());
                }
            }
        }
        return path;
    }

    protected abstract Charset initCharset(Charset characterEncoding);

    protected QueryStringDecoder createDecoder(String uri) {
        Charset charset = getCharacterEncoding();
        return charset != null ? new QueryStringDecoder(uri, charset) : new QueryStringDecoder(uri);
    }

    private String decodePath(String uri) {
        QueryStringDecoder queryStringDecoder = createDecoder(uri);
        return queryStringDecoder.rawPath();
    }

    private NettyHttpParameters decodeParameters(String uri) {
        QueryStringDecoder queryStringDecoder = createDecoder(uri);
        return new NettyHttpParameters(queryStringDecoder.parameters(), conversionService);
    }
}
