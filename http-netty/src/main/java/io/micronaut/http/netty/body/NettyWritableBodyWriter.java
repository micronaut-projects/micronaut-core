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
import io.micronaut.core.io.Writable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.ServerHttpResponse;
import io.micronaut.http.ServerHttpResponseWrapper;
import io.micronaut.http.body.ChunkedMessageBodyReader;
import io.micronaut.http.body.MessageBodyWriter;
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
public final class NettyWritableBodyWriter implements TypedMessageBodyHandler<Writable>, ChunkedMessageBodyReader<Writable> {

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
    public ServerHttpResponse<?> writeTo(ByteBufferFactory<?, ?> bufferFactory, HttpRequest<?> request, MutableHttpResponse<Writable> outgoingResponse, Argument<Writable> type, MediaType mediaType, Writable object) throws CodecException {
        MutableHttpHeaders outgoingHeaders = outgoingResponse.getHeaders();
        if (mediaType != null && !outgoingHeaders.contains(HttpHeaders.CONTENT_TYPE)) {
            outgoingHeaders.contentType(mediaType);
        }
        ByteBufOutputStream outputStream = new ByteBufOutputStream(ByteBufAllocator.DEFAULT.buffer());
        try {
            object.writeTo(outputStream, MessageBodyWriter.getCharset(mediaType, outgoingHeaders));
            outputStream.close();
        } catch (IOException e) {
            throw new MessageBodyException("Error writing body from writable", e);
        }
        return ServerHttpResponseWrapper.wrap(outgoingResponse, new AvailableNettyByteBody(outputStream.buffer()));
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
