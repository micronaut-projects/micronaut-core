/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.cookie.ClientCookieEncoder;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.MutableHttpRequestWrapper;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.netty.NettyHttpRequestBuilder;
import io.netty.handler.codec.http.HttpRequest;

import java.io.Closeable;
import java.util.Optional;

/**
 * This is a combination of a {@link HttpRequest} with a {@link ByteBody}. It implements
 * {@link MutableHttpRequest} so that it can be used unchanged in the client,
 * {@link NettyHttpRequestBuilder} so that the bytes are
 *
 * @param <B> The body type, mostly unused
 * @since 4.7.0
 */
@Internal
final class RawHttpRequestWrapper<B> extends MutableHttpRequestWrapper<B> implements MutableHttpRequest<B>, NettyHttpRequestBuilder, ServerHttpRequest<B>, Closeable {
    private final ConversionService conversionService;
    private final CloseableByteBody byteBody;
    /**
     * Whether {@link #body(Object)} replaced the raw bytes, e.g. in a client filter.
     */
    private boolean bodyReplaced;
    @Nullable
    private Object replacementBody;

    /**
     * Whether the Netty request leaves out the hop-by-hop headers.
     */
    private final boolean stripHopByHopHeaders;

    public RawHttpRequestWrapper(ConversionService conversionService, MutableHttpRequest<B> delegate, CloseableByteBody byteBody) {
        this(conversionService, delegate, byteBody, false);
    }

    public RawHttpRequestWrapper(ConversionService conversionService, MutableHttpRequest<B> delegate, CloseableByteBody byteBody, boolean stripHopByHopHeaders) {
        super(conversionService, delegate);
        this.conversionService = conversionService;
        this.byteBody = byteBody;
        this.stripHopByHopHeaders = stripHopByHopHeaders;
    }

    @Override
    public ByteBody byteBody() {
        return byteBody;
    }

    @Override
    public @Nullable ByteBody byteBodyDirect() {
        return bodyReplaced ? null : byteBody;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<B> getBody() {
        if (bodyReplaced) {
            return Optional.ofNullable((B) replacementBody);
        }
        return super.getBody();
    }

    @Override
    public <T> Optional<T> getBody(Class<T> type) {
        if (bodyReplaced) {
            return replacementBody == null ? Optional.empty() : conversionService.convert(replacementBody, ConversionContext.of(type));
        }
        return super.getBody(type);
    }

    @Override
    public <T> Optional<T> getBody(ArgumentConversionContext<T> conversionContext) {
        if (bodyReplaced) {
            return replacementBody == null ? Optional.empty() : conversionService.convert(replacementBody, conversionContext);
        }
        return super.getBody(conversionContext);
    }

    /**
     * Replace the raw bytes with the given body, which is encoded like the body of any other
     * request. The raw bytes are released.
     *
     * @param body The new body, or {@code null} to send none
     * @param <T>  The body type
     * @return This request
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> MutableHttpRequest<T> body(@Nullable T body) {
        if (!bodyReplaced) {
            bodyReplaced = true;
            byteBody.close();
        }
        replacementBody = body;
        return (MutableHttpRequest<T>) this;
    }

    @Override
    public HttpRequest toHttpRequestWithoutBody() {
        HttpRequest request = NettyHttpRequestBuilder.asBuilder(getDelegate()).toHttpRequestWithoutBody();
        if (stripHopByHopHeaders) {
            // on the Netty headers, after the client filters ran
            HopByHopHeaders.strip(request.headers());
        }
        return request;
    }

    @Override
    public MutableHttpRequest<B> cookie(Cookie cookie) {
        // the relayed request is sent with the headers of the wrapped request: a cookie a client
        // filter adds goes to its Cookie header, like for a client request
        MutableHttpHeaders headers = getHeaders();
        StringBuilder value = new StringBuilder();
        String existing = headers.get(HttpHeaders.COOKIE);
        if (existing != null) {
            // a cookie of the same name is replaced
            String prefix = cookie.getName() + "=";
            for (String pair : existing.split(";")) {
                String trimmed = pair.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith(prefix)) {
                    value.append(trimmed).append("; ");
                }
            }
        }
        headers.set(HttpHeaders.COOKIE, value.append(ClientCookieEncoder.INSTANCE.encode(cookie)).toString());
        return this;
    }

    @Override
    public void close() {
        byteBody.close();
    }
}
