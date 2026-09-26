/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.Cookies;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Default {@link MutableByteBodyHttpResponse}. Status, headers and attributes are copied into a
 * {@link MutableHttpResponse} once, the bytes stay with the original response.
 *
 * @param <B> The object body type
 * @since 5.3.0
 */
@Internal
final class DefaultMutableByteBodyHttpResponse<B> implements MutableByteBodyHttpResponse<B> {
    private final ByteBodyHttpResponse<?> original;
    private final MutableHttpResponse<B> delegate;
    private boolean bytesReplaced;
    private boolean closed;

    DefaultMutableByteBodyHttpResponse(ByteBodyHttpResponse<?> original) {
        this.original = original;
        MutableHttpResponse<B> copy = HttpResponse.status(original.code(), original.reason());
        MutableHttpHeaders headers = copy.getHeaders();
        original.getHeaders().forEach((name, values) -> {
            for (String value : values) {
                headers.add(name, value);
            }
        });
        copy.getAttributes().putAll(original.getAttributes());
        this.delegate = copy;
    }

    @Override
    public ByteBody byteBody() {
        return original.byteBody();
    }

    /**
     * @return The response whose bytes this response keeps
     */
    ByteBodyHttpResponse<?> original() {
        return original;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            original.close();
        }
    }

    @Override
    public int code() {
        return delegate.code();
    }

    @Override
    public String reason() {
        return delegate.reason();
    }

    @Override
    public MutableHttpResponse<B> status(int status, @Nullable CharSequence message) {
        delegate.status(status, message);
        return this;
    }

    @Override
    public MutableHttpHeaders getHeaders() {
        return delegate.getHeaders();
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Optional<B> getBody() {
        return delegate.getBody();
    }

    @Override
    public boolean hasByteBody() {
        // explicit: after body(object) and body(null) there is no object body, but the bytes are
        // closed all the same
        return !bytesReplaced;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> MutableHttpResponse<T> body(@Nullable T body) {
        if (body != null && !bytesReplaced) {
            bytesReplaced = true;
            close();
        }
        if (bytesReplaced) {
            delegate.body(body);
        }
        return (MutableHttpResponse<T>) this;
    }

    @Override
    public Optional<MessageBodyWriter<B>> getBodyWriter() {
        return delegate.getBodyWriter();
    }

    @Override
    public MutableHttpMessage<B> bodyWriter(MessageBodyWriter<B> messageBodyWriter) {
        delegate.bodyWriter(messageBodyWriter);
        return this;
    }

    @Override
    public MutableHttpResponse<B> cookie(Cookie cookie) {
        delegate.cookie(cookie);
        return this;
    }

    @Override
    public Cookies getCookies() {
        return delegate.getCookies();
    }

    @Override
    public Optional<Cookie> getCookie(String name) {
        return delegate.getCookie(name);
    }

    @Override
    public String toString() {
        return code() + " " + reason();
    }
}
