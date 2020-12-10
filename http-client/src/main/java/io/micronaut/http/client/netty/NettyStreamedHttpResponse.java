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
package io.micronaut.http.client.netty;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.http.*;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.Cookies;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.micronaut.http.netty.NettyHttpResponseBuilder;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.http.netty.cookies.NettyCookies;
import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;

import java.util.Objects;
import java.util.Optional;

/**
 * Wrapper object for a {@link StreamedHttpResponse}.
 *
 * @param <B> The response body type
 * @author graemerocher
 * @since 1.0
 */
@Internal
class NettyStreamedHttpResponse<B> implements MutableHttpResponse<B>, NettyHttpResponseBuilder {

    private final StreamedHttpResponse nettyResponse;
    private HttpStatus status;
    private final NettyHttpHeaders headers;
    private final NettyCookies nettyCookies;
    private B body;
    private MutableConvertibleValues<Object> attributes;

    /**
     * @param response The streamed Http response
     * @param httpStatus The Http status
     */
    NettyStreamedHttpResponse(StreamedHttpResponse response, HttpStatus httpStatus) {
        this.nettyResponse = response;
        this.status = httpStatus;
        this.headers = new NettyHttpHeaders(response.headers(), ConversionService.SHARED);
        this.nettyCookies = new NettyCookies(response.headers(), ConversionService.SHARED);
    }

    /**
     * @return The streamed Http response
     */
    public StreamedHttpResponse getNettyResponse() {
        return nettyResponse;
    }

    @Override
    public String reason() {
        return nettyResponse.status().reasonPhrase();
    }

    @Override
    public HttpStatus getStatus() {
        return status;
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
                    attributes = new MutableConvertibleValuesMap<>();
                    this.attributes = attributes;
                }
            }
        }
        return attributes;
    }

    /**
     * Sets the body.
     *
     * @param body The body
     */
    public void setBody(B body) {
        this.body = body;
    }

    @Override
    public Optional<B> getBody() {
        return Optional.ofNullable(body);
    }

    @NonNull
    @Override
    public FullHttpResponse toFullHttpResponse() {
        throw new UnsupportedOperationException("Cannot convert a stream response to a full response");
    }

    @NonNull
    @Override
    public StreamedHttpResponse toStreamHttpResponse() {
        return this.nettyResponse;
    }

    @NonNull
    @Override
    public io.netty.handler.codec.http.HttpResponse toHttpResponse() {
        return this.nettyResponse;
    }

    @Override
    public boolean isStream() {
        return true;
    }

    @Override
    public MutableHttpResponse<B> cookie(Cookie cookie) {
        if (cookie instanceof NettyCookie) {
            NettyCookie nettyCookie = (NettyCookie) cookie;
            String value = ClientCookieEncoder.STRICT.encode(nettyCookie.getNettyCookie());
            headers.add(HttpHeaderNames.SET_COOKIE, value);
        } else {
            throw new IllegalArgumentException("Argument is not a Netty compatible Cookie");
        }
        return this;
    }

    @Override
    public Cookies getCookies() {
        return nettyCookies;
    }

    @Override
    public Optional<Cookie> getCookie(String name) {
        return nettyCookies.findCookie(name);
    }

    @Override
    public <T> MutableHttpResponse<T> body(@Nullable T body) {
        this.body = (B) body;
        return (MutableHttpResponse<T>) this;
    }

    @Override
    public MutableHttpResponse<B> status(HttpStatus status, CharSequence message) {
        this.status = Objects.requireNonNull(status, "Status is required");
        return this;
    }
}
