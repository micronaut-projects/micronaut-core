/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.ChunkedMessageBodyReader;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.ResponseBodyWriter;
import io.micronaut.http.body.TypedMessageBodyHandler;
import io.micronaut.http.codec.CodecException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Handler for netty {@link ByteBuf}.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
@Singleton
@Experimental
@BootstrapContextCompatible
public final class NettyByteBufMessageBodyHandler implements TypedMessageBodyHandler<ByteBuf>, ChunkedMessageBodyReader<ByteBuf>, ResponseBodyWriter<ByteBuf> {

    @Override
    public Argument<ByteBuf> getType() {
        return Argument.of(ByteBuf.class);
    }

    @Override
    public Publisher<ByteBuf> readChunked(Argument<ByteBuf> type, MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
        return Flux.from(input).map(bb -> (ByteBuf) bb.asNativeBuffer());
    }

    @Override
    public ByteBuf read(Argument<ByteBuf> type, MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
        return (ByteBuf) byteBuffer.asNativeBuffer();
    }

    @Override
    public ByteBuf read(Argument<ByteBuf> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        try {
            return Unpooled.wrappedBuffer(inputStream.readAllBytes());
        } catch (IOException e) {
            throw new CodecException("Failed to read InputStream", e);
        }
    }

    @Override
    public void writeTo(Argument<ByteBuf> type, MediaType mediaType, ByteBuf object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        try {
            new ByteBufInputStream(object).transferTo(outputStream);
            // ByteBufInputStream#close doesn't release properly
            object.release();
        } catch (IOException e) {
            throw new CodecException("Failed to transfer byte buffer", e);
        }
    }

    @Override
    public ByteBuffer<?> writeTo(Argument<ByteBuf> type, MediaType mediaType, ByteBuf object, MutableHeaders outgoingHeaders, ByteBufferFactory<?, ?> bufferFactory) throws CodecException {
        return NettyByteBufferFactory.DEFAULT.wrap(object);
    }

    @Override
    public @NonNull CloseableByteBody writePiece(@NonNull ByteBodyFactory bodyFactory, @NonNull HttpRequest<?> request, @NonNull HttpResponse<?> response, @NonNull Argument<ByteBuf> type, @NonNull MediaType mediaType, ByteBuf object) throws CodecException {
        return new AvailableNettyByteBody(object);
    }
}
