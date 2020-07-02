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
package io.micronaut.http.netty;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.*;
import io.micronaut.http.netty.stream.DefaultStreamedHttpRequest;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.AsciiString;
import io.netty.util.DefaultAttributeMap;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

/**
 * Abstract implementation of {@link HttpRequest} for Netty.
 *
 * @param <B> The body
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public abstract class AbstractNettyHttpRequest<B> extends DefaultAttributeMap implements HttpRequest<B>, NettyHttpRequestBuilder {

    public static final AsciiString STREAM_ID = HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text();
    public static final AsciiString HTTP2_SCHEME = HttpConversionUtil.ExtensionHeaderNames.SCHEME.text();
    protected final io.netty.handler.codec.http.HttpRequest nettyRequest;
    protected final ConversionService<?> conversionService;
    protected final HttpMethod httpMethod;
    protected final URI uri;
    protected final String httpMethodName;

    private NettyHttpParameters httpParameters;
    private MediaType mediaType;
    private Charset charset;
    private Locale locale;
    private String path;
    private Collection<MediaType> accept;

    /**
     * @param nettyRequest      The Http netty request
     * @param conversionService The conversion service
     */
    public AbstractNettyHttpRequest(io.netty.handler.codec.http.HttpRequest nettyRequest, ConversionService conversionService) {
        this.nettyRequest = nettyRequest;
        this.conversionService = conversionService;
        String fullUri = nettyRequest.uri();
        this.uri = URI.create(fullUri);
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
        if (this.nettyRequest instanceof io.netty.handler.codec.http.FullHttpRequest) {
            return (io.netty.handler.codec.http.FullHttpRequest) this.nettyRequest;
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
            if (this.nettyRequest instanceof io.netty.handler.codec.http.FullHttpRequest) {

                return new DefaultStreamedHttpRequest(
                        io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                        this.nettyRequest.method(),
                        this.nettyRequest.uri(),
                        true,
                        Publishers.just(new DefaultLastHttpContent(((io.netty.handler.codec.http.FullHttpRequest) this.nettyRequest).content()))
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

    @Override
    public HttpVersion getHttpVersion() {
        if (nettyRequest.headers().contains(STREAM_ID)) {
            return HttpVersion.HTTP_2_0;
        }
        return HttpVersion.HTTP_1_1;
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
                    httpParameters = decodeParameters(nettyRequest.uri());
                    this.httpParameters = httpParameters;
                }
            }
        }
        return httpParameters;
    }

    @Override
    public Collection<MediaType> accept() {
        Collection<MediaType> accept = this.accept;
        if (accept == null) {
            synchronized (this) { // double check
                accept = this.accept;
                if (accept == null) {
                    accept = HttpRequest.super.accept();
                    this.accept = accept;
                }
            }
        }
        return accept;
    }

    @Override
    public Optional<MediaType> getContentType() {
        MediaType contentType = this.mediaType;
        if (contentType == null) {
            synchronized (this) { // double check
                contentType = this.mediaType;
                if (contentType == null) {
                    contentType = HttpRequest.super.getContentType().orElse(null);
                    this.mediaType = contentType;
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
                    charset = initCharset(HttpRequest.super.getCharacterEncoding());
                    this.charset = charset;
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
                    locale = HttpRequest.super.getLocale().orElse(null);
                    this.locale = locale;
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
                    path = decodePath(nettyRequest.uri());
                    this.path = path;
                }
            }
        }
        return path;
    }

    /**
     * @param characterEncoding The charactger encoding
     * @return The Charset
     */
    protected abstract Charset initCharset(Charset characterEncoding);

    /**
     * @param uri The URI
     * @return The query string decoder
     */
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
        return new NettyHttpParameters(queryStringDecoder.parameters(), conversionService, null);
    }

    @Override
    public String getMethodName() {
        return httpMethodName;
    }
}
