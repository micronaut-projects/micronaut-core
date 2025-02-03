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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.Writable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.ByteBodyHttpResponseWrapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.ChunkedMessageBodyReader;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.body.ResponseBodyWriter;
import io.micronaut.http.body.TypedMessageBodyHandler;
import io.micronaut.http.body.WritableBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.exceptions.MessageBodyException;
import io.micronaut.runtime.ApplicationConfiguration;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Netty-specific writer implementation.
 */
@Replaces(WritableBodyWriter.class)
@Singleton
@Internal
@BootstrapContextCompatible
public final class NettyWritableBodyWriter implements TypedMessageBodyHandler<Writable>, ChunkedMessageBodyReader<Writable>, ResponseBodyWriter<Writable> {

    private final WritableBodyWriter defaultWritable;

    public NettyWritableBodyWriter(ApplicationConfiguration applicationConfiguration) {
        defaultWritable = new WritableBodyWriter(applicationConfiguration);
    }

    @Override
    public Argument<Writable> getType() {
        return Argument.of(Writable.class);
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public ByteBodyHttpResponse<?> write(@NonNull ByteBodyFactory bodyFactory,
                                         HttpRequest<?> request,
                                         MutableHttpResponse<Writable> outgoingResponse,
                                         Argument<Writable> type,
                                         MediaType mediaType,
                                         Writable object) throws CodecException {
        outgoingResponse.getHeaders().contentTypeIfMissing(mediaType);
        return ByteBodyHttpResponseWrapper.wrap(outgoingResponse, writePiece(bodyFactory, request, outgoingResponse, type, mediaType, object));
    }

    @Override
    public CloseableByteBody writePiece(@NonNull ByteBodyFactory bodyFactory,
                                        @NonNull HttpRequest<?> request,
                                        @NonNull HttpResponse<?> response,
                                        @NonNull Argument<Writable> type,
                                        @NonNull MediaType mediaType,
                                        Writable object) {
        ByteBufOutputStream outputStream = new ByteBufOutputStream(ByteBufAllocator.DEFAULT.buffer());
        try {
            object.writeTo(outputStream, MessageBodyWriter.getCharset(mediaType, response.getHeaders()));
            outputStream.close();
        } catch (IOException e) {
            throw new MessageBodyException("Error writing body from writable", e);
        }
        return new AvailableNettyByteBody(outputStream.buffer());
    }

    @Override
    public void writeTo(Argument<Writable> type, MediaType mediaType, Writable object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        defaultWritable.writeTo(type, mediaType, object, outgoingHeaders, outputStream);
    }

    @Override
    public Publisher<? extends Writable> readChunked(Argument<Writable> type, MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
        return defaultWritable.readChunked(type, mediaType, httpHeaders, input);
    }

    @Override
    public Writable read(Argument<Writable> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        return defaultWritable.read(type, mediaType, httpHeaders, inputStream);
    }

}
