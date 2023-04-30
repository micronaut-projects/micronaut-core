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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpParameters;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.netty.stream.DefaultStreamedHttpRequest;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.DefaultAttributeMap;

import java.net.URI;
import java.net.URISyntaxException;
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

    protected final io.netty.handler.codec.http.HttpRequest nettyRequest;
    protected final ConversionService conversionService;
    protected final HttpMethod httpMethod;
    protected final URI uri;
    protected final String httpMethodName;

    private NettyHttpParameters httpParameters;
    private Optional<MediaType> mediaType;
    private Charset charset;
    private Optional<Locale> locale;
    private String path;
    private Collection<MediaType> accept;

    /**
     * @param nettyRequest      The Http netty request
     * @param conversionService The conversion service
     */
    public AbstractNettyHttpRequest(io.netty.handler.codec.http.HttpRequest nettyRequest, ConversionService conversionService) {
        this.nettyRequest = nettyRequest;
        this.conversionService = conversionService;
        URI fullUri = URI.create(nettyRequest.uri());
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
        this.uri = fullUri;
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
    public Collection<MediaType> accept() {
        if (accept == null) {
            accept = HttpRequest.super.accept();
        }
        return accept;
    }

    @Override
    @SuppressWarnings("java:S2789") // performance opt
    public Optional<MediaType> getContentType() {
        if (mediaType == null) {
            mediaType = HttpRequest.super.getContentType();
        }
        return mediaType;
    }

    @Override
    public Charset getCharacterEncoding() {
        if (charset == null) {
            charset = initCharset(HttpRequest.super.getCharacterEncoding());
        }
        return charset;
    }

    @Override
    @SuppressWarnings("java:S2789") // performance opt
    public Optional<Locale> getLocale() {
        if (locale == null) {
            locale = HttpRequest.super.getLocale();
        }
        return locale;
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
        String p = this.path;
        if (p == null) {
            synchronized (this) { // double check
                p = this.path;
                if (p == null) {
                    p = decodePath();
                    this.path = p;
                }
            }
        }
        return p;
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
    @SuppressWarnings("ConstantConditions")
    protected final QueryStringDecoder createDecoder(URI uri) {
        Charset cs = getCharacterEncoding();
        return cs != null ? new QueryStringDecoder(uri, cs) : new QueryStringDecoder(uri);
    }

    private String decodePath() {
        QueryStringDecoder queryStringDecoder = createDecoder(uri);
        return queryStringDecoder.rawPath();
    }

    private NettyHttpParameters decodeParameters() {
        QueryStringDecoder queryStringDecoder = createDecoder(uri);
        return new NettyHttpParameters(queryStringDecoder.parameters(), conversionService, null);
    }

    @Override
    public String getMethodName() {
        return httpMethodName;
    }
}
