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
package io.micronaut.http.netty.body;

import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.ChunkedMessageBodyReader;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.MessageBodyHandler;
import io.micronaut.http.body.PieceReader;
import io.micronaut.http.body.PieceWriter;
import io.micronaut.http.body.ResponseBodyWriter;
import io.micronaut.json.body.CustomizableJsonHandler;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Compatibility delegation; parsing and writing remain in json-core.
 *
 * @param <T> The body type
 * @param <H> The delegate type
 */
abstract class DelegatingJsonHandler<T, H extends MessageBodyHandler<T> & ChunkedMessageBodyReader<T> & CustomizableJsonHandler & ResponseBodyWriter<T>>
    implements MessageBodyHandler<T>, ChunkedMessageBodyReader<T>, CustomizableJsonHandler, ResponseBodyWriter<T> {

    final H delegate;

    DelegatingJsonHandler(H delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean isReadable(Argument<T> type, @Nullable MediaType mediaType) {
        return delegate.isReadable(type, mediaType);
    }

    @Override
    public @Nullable T read(Argument<T> type, @Nullable MediaType mediaType, Headers headers, ByteBuffer<?> buffer) {
        return delegate.read(type, mediaType, headers, buffer);
    }

    @Override
    public @Nullable T read(Argument<T> type, @Nullable MediaType mediaType, Headers headers, InputStream input) {
        return delegate.read(type, mediaType, headers, input);
    }

    @Override
    @SuppressWarnings("unchecked") // the reader emits values of the requested type
    public Publisher<T> readChunked(Argument<T> type, @Nullable MediaType mediaType, Headers headers, Publisher<ByteBuffer<?>> input) {
        return (Publisher<T>) delegate.readChunked(type, mediaType, headers, input);
    }

    @Override
    @SuppressWarnings("unchecked") // the reader emits values of the requested type
    public Publisher<T> readChunked(Argument<T> type, @Nullable MediaType mediaType, Headers headers, Publisher<ByteBuffer<?>> input, long maxElementSize) {
        return (Publisher<T>) delegate.readChunked(type, mediaType, headers, input, maxElementSize);
    }

    @Override
    public @Nullable PieceReader<T> openPieceReader(Argument<T> type, @Nullable MediaType mediaType, Headers headers, long maxElementSize) {
        return delegate.openPieceReader(type, mediaType, headers, maxElementSize);
    }

    @Override
    public boolean isWriteable(Argument<T> type, @Nullable MediaType mediaType) {
        return delegate.isWriteable(type, mediaType);
    }

    @Override
    public void writeTo(Argument<T> type, MediaType mediaType, T value, MutableHeaders headers, OutputStream output) {
        delegate.writeTo(type, mediaType, value, headers, output);
    }

    @Override
    public ByteBuffer<?> writeTo(Argument<T> type, MediaType mediaType, T value, MutableHeaders headers, ByteBufferFactory<?, ?> factory) {
        return delegate.writeTo(type, mediaType, value, headers, factory);
    }

    @Override
    public ByteBodyHttpResponse<?> write(ByteBodyFactory factory, HttpRequest<?> request, MutableHttpResponse<T> response, Argument<T> type, MediaType mediaType, T value) {
        return delegate.write(factory, request, response, type, mediaType, value);
    }

    @Override
    public CloseableByteBody writePiece(ByteBodyFactory factory, HttpRequest<?> request, HttpResponse<?> response, Argument<T> type, MediaType mediaType, T value) {
        return delegate.writePiece(factory, request, response, type, mediaType, value);
    }

    @Override
    public PieceWriter<T> openPieceWriter(ByteBodyFactory factory, HttpRequest<?> request, HttpResponse<?> response, Argument<T> type, MediaType mediaType) {
        return delegate.openPieceWriter(factory, request, response, type, mediaType);
    }

    @Override
    public boolean isBlocking() {
        return delegate.isBlocking();
    }
}
