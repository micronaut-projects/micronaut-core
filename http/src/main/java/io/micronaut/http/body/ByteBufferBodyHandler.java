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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The body handler for {@link ByteBuffer}.
 *
 * @author Denis Stepanov
 * @since 4.6
 */
@Requires(bean = ByteBufferFactory.class)
@Singleton
@BootstrapContextCompatible
final class ByteBufferBodyHandler implements TypedMessageBodyHandler<ByteBuffer<?>>, ChunkedMessageBodyReader<ByteBuffer<?>> {

    private final ByteBufferFactory<?, ?> byteBufferFactory;

    ByteBufferBodyHandler(ByteBufferFactory<?, ?> byteBufferFactory) {
        this.byteBufferFactory = byteBufferFactory;
    }

    @Override
    public Argument<ByteBuffer<?>> getType() {
        return Argument.of((Class) ByteBuffer.class);
    }

    @Override
    public ByteBuffer<?> read(Argument<ByteBuffer<?>> type, MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
        return byteBuffer;
    }

    @Override
    public ByteBuffer<?> read(Argument<ByteBuffer<?>> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        try {
            return byteBufferFactory.wrap(inputStream.readAllBytes());
        } catch (IOException e) {
            throw new CodecException("Failed to read InputStream", e);
        }
    }

    @Override
    public void writeTo(Argument<ByteBuffer<?>> type, MediaType mediaType, ByteBuffer<?> object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        addContentType(outgoingHeaders, mediaType);
        try {
            object.toInputStream().transferTo(outputStream);
            if (object instanceof ReferenceCounted rc) {
                rc.release();
            }
        } catch (IOException e) {
            throw new CodecException("Failed to write OutputStream", e);
        }
    }

    @Override
    public ByteBuffer<?> writeTo(Argument<ByteBuffer<?>> type, MediaType mediaType, ByteBuffer<?> object, MutableHeaders outgoingHeaders, ByteBufferFactory<?, ?> bufferFactory) throws CodecException {
        addContentType(outgoingHeaders, mediaType);
        return object;
    }

    private static void addContentType(MutableHeaders outgoingHeaders, @Nullable MediaType mediaType) {
        if (mediaType != null) {
            outgoingHeaders.setIfMissing(HttpHeaders.CONTENT_TYPE, mediaType);
        }
    }

    @Override
    public Publisher<ByteBuffer<?>> readChunked(Argument<ByteBuffer<?>> type, MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
        return input;
    }

}
