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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.ChunkedMessageBodyReader;
import io.micronaut.http.body.MessageBodyHandler;
import io.micronaut.http.codec.CodecException;
import io.micronaut.json.JsonFeatures;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.body.CustomizableJsonHandler;
import io.micronaut.json.body.JsonMessageHandler;
import io.netty.buffer.ByteBuf;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Netty json stream implementation for MessageBodyHandler.
 *
 * @param <T> The type
 *
 * @since 4.0.0
 */
@Internal
@Singleton
@Produces(MediaType.APPLICATION_JSON_STREAM)
@Consumes(MediaType.APPLICATION_JSON_STREAM)
public final class NettyJsonStreamHandler<T> implements MessageBodyHandler<T>, ChunkedMessageBodyReader<T>, CustomizableJsonHandler {
    private final JsonMessageHandler<T> jsonMessageHandler;

    public NettyJsonStreamHandler(JsonMapper jsonMapper) {
        this(new JsonMessageHandler<>(jsonMapper));
    }

    private NettyJsonStreamHandler(JsonMessageHandler<T> jsonMessageHandler) {
        this.jsonMessageHandler = jsonMessageHandler;
    }

    @Override
    public CustomizableJsonHandler customize(JsonFeatures jsonFeatures) {
        return new NettyJsonStreamHandler<>(jsonMessageHandler.getJsonMapper().cloneWithFeatures(jsonFeatures));
    }

    @Override
    public boolean isReadable(Argument<T> type, MediaType mediaType) {
        return mediaType.matches(MediaType.APPLICATION_JSON_STREAM_TYPE);
    }

    @Override
    public T read(Argument<T> type, MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
        if (!type.getType().isAssignableFrom(List.class)) {
            throw new IllegalArgumentException("Can only read json-stream to a Publisher or list type");
        }
        //noinspection unchecked
        return (T) readChunked((Argument<T>) type.getFirstTypeVariable().orElse(type), mediaType, httpHeaders, Flux.just(byteBuffer)).collectList().block();
    }

    @Override
    public T read(Argument<T> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        throw new UnsupportedOperationException("Reading from InputStream is not supported for json-stream");
    }

    @Override
    public Flux<T> readChunked(Argument<T> type, MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
        JsonChunkedProcessor processor = new JsonChunkedProcessor();
        return processor.process(Flux.from(input).map(bb -> {
            if (!(bb.asNativeBuffer() instanceof ByteBuf buf)) {
                throw new IllegalArgumentException("Only netty buffers are supported");
            }
            return buf;
        })).map(bb -> jsonMessageHandler.read(type, mediaType, httpHeaders, bb));
    }

    @Override
    public void writeTo(Argument<T> type, MediaType mediaType, T object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        jsonMessageHandler.writeTo(type, mediaType, object, outgoingHeaders, outputStream);
    }

    @Override
    public ByteBuffer<?> writeTo(Argument<T> type, MediaType mediaType, T object, MutableHeaders outgoingHeaders, ByteBufferFactory<?, ?> bufferFactory) throws CodecException {
        return jsonMessageHandler.writeTo(type, mediaType, object, outgoingHeaders, bufferFactory);
    }
}
