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
import org.jspecify.annotations.Nullable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.ByteBodyHttpResponseWrapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.codec.CodecException;

import java.io.OutputStream;

/**
 * {@link ResponseBodyWriter} implementation that delegates to a {@link MessageBodyWriter}.
 *
 * @param <T> The body type
 * @since 4.7.0
 * @author Jonas Konrad
 */
@Internal
final class ResponseBodyWriterWrapper<T> implements ResponseBodyWriter<T> {
    private final MessageBodyWriter<T> wrapped;

    ResponseBodyWriterWrapper(MessageBodyWriter<T> wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public boolean isWriteable(Argument<T> type, @Nullable MediaType mediaType) {
        return wrapped.isWriteable(type, mediaType);
    }

    @Override
    public MessageBodyWriter<T> createSpecific(Argument<T> type) {
        return wrapped.createSpecific(type);
    }

    @Override
    public boolean isBlocking() {
        return wrapped.isBlocking();
    }

    @Override
    public void writeTo(Argument<T> type, MediaType mediaType, T object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        wrapped.writeTo(type, mediaType, object, outgoingHeaders, outputStream);
    }

    @Override
    public ByteBuffer<?> writeTo(Argument<T> type, MediaType mediaType, T object, MutableHeaders outgoingHeaders, ByteBufferFactory<?, ?> bufferFactory) throws CodecException {
        return wrapped.writeTo(type, mediaType, object, outgoingHeaders, bufferFactory);
    }

    @Override
    public ByteBodyHttpResponse<?> write(ByteBodyFactory bodyFactory, HttpRequest<?> request, MutableHttpResponse<T> httpResponse, Argument<T> type, MediaType mediaType, T object) throws CodecException {
        return ByteBodyHttpResponseWrapper.wrap(httpResponse, writePiece(bodyFactory, httpResponse.getHeaders(), type, mediaType, object));
    }

    @Override
    public CloseableByteBody writePiece(ByteBodyFactory bodyFactory, HttpRequest<?> request, HttpResponse<?> response, Argument<T> type, MediaType mediaType, T object) {
        return writePiece(bodyFactory, response.toMutableResponse().getHeaders(), type, mediaType, object);
    }

    private CloseableByteBody writePiece(ByteBodyFactory bodyFactory, MutableHttpHeaders headers, Argument<T> type, MediaType mediaType, T object) {
        return bodyFactory.buffer(s -> writeTo(type, mediaType, object, headers, s));
    }
}
