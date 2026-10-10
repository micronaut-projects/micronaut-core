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
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.stream.PieceReaders;
import io.micronaut.http.codec.CodecException;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * The body handler for byte[].
 *
 * @author Denis Stepanov
 * @since 4.6
 */
@Singleton
@BootstrapContextCompatible
@Internal
final class ByteArrayBodyHandler implements TypedMessageBodyHandler<byte[]>, ChunkedMessageBodyReader<byte[]>, ResponseBodyWriter<byte[]> {
    /**
     * Pieces up to this size are copied to the heap: the transport combines small pieces into
     * larger buffers anyway, and a heap copy needs no pooled buffer. Larger pieces are copied
     * into a buffer of the body factory once.
     */
    private static final int HEAP_COPY_LIMIT = 1024;

    /**
     * The type this handler is for, resolved once: isWriteable asks for it on every response.
     */
    private static final Argument<byte[]> TYPE = Argument.of(byte[].class);

    @Override
    public Argument<byte[]> getType() {
        return TYPE;
    }

    @Override
    public byte[] read(Argument<byte[]> type, @Nullable MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
        return read0(byteBuffer);
    }

    private static byte[] read0(ByteBuffer<?> byteBuffer) {
        byte[] arr = byteBuffer.toByteArray();
        if (byteBuffer instanceof ReferenceCounted rc) {
            rc.release();
        }
        return arr;
    }

    @Override
    public byte[] read(Argument<byte[]> type, @Nullable MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        try {
            return inputStream.readAllBytes();
        } catch (IOException e) {
            throw new CodecException("Failed to read InputStream", e);
        }
    }

    @Override
    public void writeTo(Argument<byte[]> type, MediaType mediaType, byte[] object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        addContentType(outgoingHeaders, mediaType);
        try {
            outputStream.write(object);
        } catch (IOException e) {
            throw new CodecException("Failed to write OutputStream", e);
        }
    }

    @Override
    public ByteBuffer<?> writeTo(Argument<byte[]> type, MediaType mediaType, byte[] object, MutableHeaders outgoingHeaders, ByteBufferFactory<?, ?> bufferFactory) throws CodecException {
        addContentType(outgoingHeaders, mediaType);
        return bufferFactory.wrap(object);
    }

    @Override
    public CloseableByteBody writePiece(ByteBodyFactory bodyFactory, HttpRequest<?> request, HttpResponse<?> response, Argument<byte[]> type, MediaType mediaType, byte[] object) throws CodecException {
        addContentType(response.toMutableResponse().getHeaders(), mediaType);
        // the array is copied: a publisher may reuse it once the piece is emitted
        if (object.length <= HEAP_COPY_LIMIT) {
            return bodyFactory.adapt(Arrays.copyOf(object, object.length));
        }
        return bodyFactory.adapt(bodyFactory.readBufferFactory().copyOf(java.nio.ByteBuffer.wrap(object)));
    }

    @Override
    public Publisher<byte[]> readChunked(Argument<byte[]> type, @Nullable MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
        return Flux.from(input).map(ByteArrayBodyHandler::read0);
    }

    @Override
    public PieceReader<byte[]> openPieceReader(Argument<byte[]> type, @Nullable MediaType mediaType, Headers httpHeaders, long maxElementSize) {
        return PieceReaders.eachPiece(ByteArrayBodyHandler::read0);
    }

    private static void addContentType(MutableHeaders outgoingHeaders, @Nullable MediaType mediaType) {
        if (mediaType != null) {
            outgoingHeaders.setIfMissing(HttpHeaders.CONTENT_TYPE, mediaType);
        }
    }

}
