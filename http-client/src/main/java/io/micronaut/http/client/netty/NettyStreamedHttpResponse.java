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

import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.CookieUtils;
import io.micronaut.http.cookie.Cookies;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.micronaut.http.netty.NettyHttpResponseBuilder;
import io.micronaut.http.netty.cookies.NettyCookies;
import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.Optional;

/**
 * Wrapper object for a {@link StreamedHttpResponse}.
 *
 * @param <B> The response body type
 * @author graemerocher
 * @since 1.0
 */
@Internal
final class NettyStreamedHttpResponse<B> implements MutableHttpResponse<B>, NettyHttpResponseBuilder {
    private final StreamedHttpResponse nettyResponse;
    private final NettyHttpHeaders headers;
    @GuardedBy("this")
    private NettyCookies nettyCookies; // initialized lazily
    private B body;
    private MutableConvertibleValues<Object> attributes;

    /**
     * @param response The streamed Http response
     * @param conversionService The conversion service
     */
    NettyStreamedHttpResponse(StreamedHttpResponse response, ConversionService conversionService) {
        this.nettyResponse = response;
        this.headers = new NettyHttpHeaders(response.headers(), conversionService);
    }

    /**
     * @return The streamed Http response
     */
    public StreamedHttpResponse getNettyResponse() {
        return nettyResponse;
    }

    @Override
    public int code() {
        return nettyResponse.status().code();
    }

    @Override
    public String reason() {
        return nettyResponse.status().reasonPhrase();
    }

    @Override
    public MutableHttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        MutableConvertibleValues<Object> mcv = this.attributes;
        if (mcv == null) {
            synchronized (this) { // double check
                mcv = this.attributes;
                if (mcv == null) {
                    mcv = new MutableConvertibleValuesMap<>();
                    this.attributes = mcv;
                }
            }
        }
        return mcv;
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
    public synchronized MutableHttpResponse<B> cookie(Cookie cookie) {
        CookieUtils.setCookieHeader(headers, cookie);
        nettyCookies = null; // need to rebuild cookie map
        return this;
    }

    @Override
    public synchronized Cookies getCookies() {
        if (nettyCookies == null) {
            nettyCookies = new NettyCookies(nettyResponse.headers(), ConversionService.SHARED);
        }
        return nettyCookies;
    }

    @Override
    public Optional<Cookie> getCookie(String name) {
        return getCookies().findCookie(name);
    }

    @Override
    public <T> MutableHttpResponse<T> body(@Nullable T body) {
        this.body = (B) body;
        return (MutableHttpResponse<T>) this;
    }

    @Override
    public MutableHttpResponse<B> status(int status, CharSequence message) {
        if (message == null) {
            nettyResponse.setStatus(HttpResponseStatus.valueOf(status));
        } else {
            nettyResponse.setStatus(HttpResponseStatus.valueOf(status, message.toString()));
        }
        return this;
    }
}
