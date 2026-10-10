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
package io.micronaut.json.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.ByteBodyHttpResponseWrapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.ChunkedMessageBodyReader;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.MessageBodyHandler;
import io.micronaut.http.body.PieceReader;
import io.micronaut.http.body.PieceWriter;
import io.micronaut.http.body.ResponseBodyWriter;
import io.micronaut.http.body.stream.ForeignBufferReleaser;
import io.micronaut.http.codec.CodecException;
import io.micronaut.json.JsonFeatures;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Body handler for a JSON stream, {@link MediaType#APPLICATION_JSON_STREAM}: whitespace
 * separated JSON values, read piecewise as the bytes of the body arrive, or bound as a list.
 *
 * @param <T> The type
 * @author Jonas Konrad
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
@Produces(MediaType.APPLICATION_JSON_STREAM)
@Consumes(MediaType.APPLICATION_JSON_STREAM)
public final class JsonStreamMessageHandler<T> implements MessageBodyHandler<T>, ChunkedMessageBodyReader<T>, CustomizableJsonHandler, ResponseBodyWriter<T> {
    private final JsonMessageHandler<T> jsonMessageHandler;

    public JsonStreamMessageHandler(JsonMapper jsonMapper) {
        this(new JsonMessageHandler<>(jsonMapper));
    }

    /**
     * @param jsonMapper     The mapper
     * @param bufferReleaser Releases the buffers of the runtime that a Reactor input of
     *                       {@link #readChunked} discards, e.g. Netty buffers, or {@code null}
     */
    @Inject
    public JsonStreamMessageHandler(JsonMapper jsonMapper, @Nullable ForeignBufferReleaser bufferReleaser) {
        this(new JsonMessageHandler<>(jsonMapper, bufferReleaser));
    }

    private JsonStreamMessageHandler(JsonMessageHandler<T> jsonMessageHandler) {
        this.jsonMessageHandler = jsonMessageHandler;
    }

    @Override
    public CustomizableJsonHandler customize(JsonFeatures jsonFeatures) {
        return new JsonStreamMessageHandler<>(jsonMessageHandler.getJsonMapper().cloneWithFeatures(jsonFeatures), jsonMessageHandler.bufferReleaser());
    }

    @Override
    public JsonStreamMessageHandler<T> createSpecific(Argument<T> type) {
        return new JsonStreamMessageHandler<>(jsonMessageHandler.createSpecific(type));
    }

    @Override
    public boolean isReadable(Argument<T> type, @Nullable MediaType mediaType) {
        return mediaType != null && mediaType.matches(MediaType.APPLICATION_JSON_STREAM_TYPE);
    }

    @Override
    public T read(Argument<T> type, @Nullable MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
        if (!type.getType().isAssignableFrom(List.class)) {
            throw new IllegalArgumentException("Can only read json-stream to a Publisher or list type");
        }
        @SuppressWarnings("unchecked")
        Argument<T> elementType = (Argument<T>) type.getFirstTypeVariable().orElse(type);
        // the values of the whole stream, without Reactive Streams
        List<ReadBuffer> values = new ArrayList<>();
        JsonChunkedProcessor processor = new JsonChunkedProcessor();
        try {
            processor.feed(SharedReadBuffer.adapt(byteBuffer), values::add);
            processor.finish(values::add);
        } catch (IOException e) {
            values.forEach(ReadBuffer::close);
            throw new CodecException("Error decoding JSON stream for type [" + elementType.getName() + "]: " + e.getMessage(), e);
        } finally {
            processor.discard();
        }
        List<Object> elements = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            ReadBuffer value = values.get(i);
            try {
                T element = jsonMessageHandler.readValue(elementType, value);
                if (element == null) {
                    // a JSON null is not an element, as the reactive readers refuse it
                    throw new CodecException("A JSON null is not an element of a JSON array or stream");
                }
                elements.add(element);
            } catch (RuntimeException e) {
                values.subList(i + 1, values.size()).forEach(ReadBuffer::close);
                throw e;
            } finally {
                value.close();
            }
        }
        //noinspection unchecked
        return (T) elements;
    }

    @Override
    public T read(Argument<T> type, @Nullable MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        throw new UnsupportedOperationException("Reading from InputStream is not supported for json-stream");
    }

    @Override
    public Publisher<T> readChunked(Argument<T> type, @Nullable MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
        return readChunked(type, mediaType, httpHeaders, input, Long.MAX_VALUE);
    }

    @Override
    public Publisher<T> readChunked(Argument<T> type, @Nullable MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input, long maxElementSize) {
        // the buffers of the input are shared with the values, not copied
        return JsonMessageHandler.publisherOfBuffers(input, openPieceReader(type, mediaType, httpHeaders, maxElementSize), jsonMessageHandler.bufferReleaser());
    }

    @Override
    public PieceReader<T> openPieceReader(Argument<T> type, @Nullable MediaType mediaType, Headers httpHeaders, long maxElementSize) {
        return new JsonPieceReader<>(new JsonChunkedProcessor(maxElementSize), value -> jsonMessageHandler.readValue(type, value));
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
    public ByteBodyHttpResponse<?> write(ByteBodyFactory bodyFactory, HttpRequest<?> request, MutableHttpResponse<T> httpResponse, Argument<T> type, MediaType mediaType, T object) throws CodecException {
        return ByteBodyHttpResponseWrapper.wrap(httpResponse, bodyFactory.buffer(s -> writeTo(type, mediaType, object, httpResponse.getHeaders(), s)));
    }

    @Override
    public CloseableByteBody writePiece(ByteBodyFactory bodyFactory, HttpRequest<?> request, HttpResponse<?> response, Argument<T> type, MediaType mediaType, T object) throws CodecException {
        return bodyFactory.buffer(s -> writeTo(type, mediaType, object, response.toMutableResponse().getHeaders(), s));
    }

    @Override
    public PieceWriter<T> openPieceWriter(ByteBodyFactory bodyFactory, HttpRequest<?> request, HttpResponse<?> response, Argument<T> type, MediaType mediaType) throws CodecException {
        return jsonMessageHandler.openPieceWriter(bodyFactory, request, response, type, mediaType);
    }
}
