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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.ChunkedMessageBodyReader;
import io.micronaut.http.body.MessageBodyHandler;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.micronaut.json.JsonFeatures;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.body.CustomizableJsonHandler;
import io.micronaut.json.body.JsonMessageHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Replacement for {@link JsonMessageHandler} with {@link ChunkedMessageBodyReader} support.
 *
 * @param <T> The type
 */
@Order(JsonMessageHandler.ORDER)
@Singleton
@Internal
@Replaces(JsonMessageHandler.class)
@JsonMessageHandler.ProducesJson
@JsonMessageHandler.ConsumesJson
@BootstrapContextCompatible
@Requires(beans = JsonMapper.class)
public final class NettyJsonHandler<T> implements MessageBodyHandler<T>, ChunkedMessageBodyReader<T>, CustomizableJsonHandler, NettyBodyWriter<T> {
    private final JsonMessageHandler<T> jsonMessageHandler;

    public NettyJsonHandler(JsonMapper jsonMapper) {
        this(new JsonMessageHandler<>(jsonMapper));
    }

    private NettyJsonHandler(JsonMessageHandler<T> jsonMessageHandler) {
        this.jsonMessageHandler = jsonMessageHandler;
    }

    @Override
    public CustomizableJsonHandler customize(JsonFeatures jsonFeatures) {
        return new NettyJsonHandler<>(jsonMessageHandler.getJsonMapper().cloneWithFeatures(jsonFeatures));
    }

    @Override
    public Publisher<T> readChunked(Argument<T> type, MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
        JsonChunkedProcessor processor = new JsonChunkedProcessor();
        if (Iterable.class.isAssignableFrom(type.getType())) {
            // Publisher<List<T>> is parsed as a single item of type List
            processor.counter.noTokenization();
        } else {
            // Publisher<T> is unwrapped
            processor.counter.unwrapTopLevelArray();
        }
        return processor.process(Flux.from(input).map(bb -> {
            if (!(bb.asNativeBuffer() instanceof ByteBuf buf)) {
                throw new IllegalArgumentException("Only netty buffers are supported");
            }
            return buf;
        })).map(bb -> read(type, mediaType, httpHeaders, bb));
    }

    @Override
    public boolean isReadable(Argument<T> type, MediaType mediaType) {
        return jsonMessageHandler.isReadable(type, mediaType);
    }

    @Override
    public T read(Argument<T> type, MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
        return jsonMessageHandler.read(type, mediaType, httpHeaders, byteBuffer);
    }

    @Override
    public T read(Argument<T> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        return jsonMessageHandler.read(type, mediaType, httpHeaders, inputStream);
    }

    @Override
    public boolean isWriteable(Argument<T> type, MediaType mediaType) {
        return jsonMessageHandler.isWriteable(type, mediaType);
    }

    @Override
    public void writeTo(Argument<T> type, MediaType mediaType, T object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        jsonMessageHandler.writeTo(type, mediaType, object, outgoingHeaders, outputStream);
    }

    @Override
    public ByteBuffer<?> writeTo(Argument<T> type, MediaType mediaType, T object, MutableHeaders outgoingHeaders, ByteBufferFactory<?, ?> bufferFactory) throws CodecException {
        return jsonMessageHandler.writeTo(type, mediaType, object, outgoingHeaders, bufferFactory);
    }

    @Override
    public void writeTo(@NonNull HttpRequest<?> request, @NonNull MutableHttpResponse<T> outgoingResponse, @NonNull Argument<T> type, @NonNull MediaType mediaType, @NonNull T object, @NonNull NettyWriteContext nettyContext) throws CodecException {
        NettyHttpHeaders nettyHttpHeaders = (NettyHttpHeaders) outgoingResponse.getHeaders();
        nettyHttpHeaders.setIfMissing(HttpHeaderNames.CONTENT_TYPE, mediaType);
        ByteBuf buffer = nettyContext.alloc().buffer();
        JsonMapper jsonMapper = jsonMessageHandler.getJsonMapper();
        try {
            jsonMapper.writeValue(new ByteBufOutputStream(buffer), object);
        } catch (IOException e) {
            buffer.release();
            throw new CodecException("Error encoding object [" + object + "] to JSON: " + e.getMessage(), e);
        }
        nettyContext.writeFull(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(outgoingResponse.code(), outgoingResponse.reason()), buffer, nettyHttpHeaders.getNettyHeaders(), EmptyHttpHeaders.INSTANCE));
    }

    @Override
    public MessageBodyWriter<T> createSpecific(Argument<T> type) {
        return new NettyJsonHandler<>(jsonMessageHandler.createSpecific(type));
    }

    @Override
    public boolean isBlocking() {
        return jsonMessageHandler.isBlocking();
    }
}
