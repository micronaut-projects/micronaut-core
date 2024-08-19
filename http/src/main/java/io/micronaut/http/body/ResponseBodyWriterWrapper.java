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
package io.micronaut.http.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.ServerHttpResponse;
import io.micronaut.http.ServerHttpResponseWrapper;
import io.micronaut.http.body.stream.AvailableByteArrayBody;
import io.micronaut.http.codec.CodecException;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

/**
 * {@link ResponseBodyWriter} implementation that delegates to a {@link MessageBodyWriter}.
 *
 * @param <T> The body type
 * @since 4.7.0
 * @author Jonas Konrad
 */
@Internal
public class ResponseBodyWriterWrapper<T> implements ResponseBodyWriter<T> {
    private final MessageBodyWriter<T> wrapped;

    protected ResponseBodyWriterWrapper(MessageBodyWriter<T> wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public boolean isWriteable(@NonNull Argument<T> type, @Nullable MediaType mediaType) {
        return wrapped.isWriteable(type, mediaType);
    }

    @Override
    public MessageBodyWriter<T> createSpecific(@NonNull Argument<T> type) {
        return wrapped.createSpecific(type);
    }

    @Override
    public boolean isBlocking() {
        return wrapped.isBlocking();
    }

    @Override
    public void writeTo(@NonNull Argument<T> type, @NonNull MediaType mediaType, T object, @NonNull MutableHeaders outgoingHeaders, @NonNull OutputStream outputStream) throws CodecException {
        wrapped.writeTo(type, mediaType, object, outgoingHeaders, outputStream);
    }

    @Override
    public @NonNull ByteBuffer<?> writeTo(@NonNull Argument<T> type, @NonNull MediaType mediaType, T object, @NonNull MutableHeaders outgoingHeaders, @NonNull ByteBufferFactory<?, ?> bufferFactory) throws CodecException {
        return wrapped.writeTo(type, mediaType, object, outgoingHeaders, bufferFactory);
    }

    @Override
    public @NonNull ServerHttpResponse<?> write(@NonNull ByteBufferFactory<?, ?> bufferFactory, @NonNull HttpRequest<?> request, @NonNull MutableHttpResponse<T> httpResponse, @NonNull Argument<T> type, @NonNull MediaType mediaType, T object) throws CodecException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeTo(type, mediaType, object, httpResponse.getHeaders(), baos);
        return ServerHttpResponseWrapper.wrap(httpResponse, AvailableByteArrayBody.create(bufferFactory, baos.toByteArray()));
    }
}
