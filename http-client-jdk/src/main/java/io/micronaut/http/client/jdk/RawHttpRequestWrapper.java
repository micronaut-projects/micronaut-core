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
package io.micronaut.http.client.jdk;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpRequestWrapper;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.netty.NettyHttpRequestBuilder;
import io.netty.handler.codec.http.HttpRequest;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.util.Optional;

/**
 * This is a combination of a {@link HttpRequest} with a {@link ByteBody}. It implements
 * {@link MutableHttpRequest} so that it can be used unchanged in the client,
 * {@link NettyHttpRequestBuilder} so that the bytes are
 *
 * @param <B> The body type, mostly unused
 * @since 4.8.0
 */
@NullUnmarked
@Internal
final class RawHttpRequestWrapper<B> extends MutableHttpRequestWrapper<B> implements MutableHttpRequest<B>, ServerHttpRequest<B>, Closeable {
    private final ConversionService conversionService;
    private final CloseableByteBody byteBody;
    /**
     * Whether {@link #body(Object)} replaced the raw bytes, e.g. in a client filter.
     */
    private boolean bodyReplaced;
    @Nullable
    private Object replacementBody;

    public RawHttpRequestWrapper(ConversionService conversionService, MutableHttpRequest<B> delegate, CloseableByteBody byteBody) {
        super(conversionService, delegate);
        this.conversionService = conversionService;
        this.byteBody = byteBody;
    }

    @Override
    public ByteBody byteBody() {
        return byteBody;
    }

    /**
     * @return Whether {@link #body(Object)} replaced the raw bytes, which are then not sent
     */
    boolean isBodyReplaced() {
        return bodyReplaced;
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
    public void close() {
        byteBody.close();
    }
}
