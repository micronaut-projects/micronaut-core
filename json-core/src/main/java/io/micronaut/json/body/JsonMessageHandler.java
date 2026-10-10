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
package io.micronaut.json.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.ByteBodyHttpResponseWrapper;
import io.micronaut.http.HttpHeaders;
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
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.body.PieceReader;
import io.micronaut.http.body.PieceWriter;
import io.micronaut.http.body.ResponseBodyWriter;
import io.micronaut.http.body.stream.ForeignBufferReleaser;
import io.micronaut.http.body.stream.PieceReaders;
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
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Body handler for JSON. It also reads the elements of a JSON array, or the values of a JSON
 * stream, piecewise as the bytes of the body arrive, see {@link ChunkedMessageBodyReader}.
 *
 * @param <T> The type to read/write
 * @author Jonas Konrad
 * @since 4.0.0
 */
@Order(JsonMessageHandler.ORDER)
@Experimental
@Singleton
@JsonMessageHandler.ProducesJson
@JsonMessageHandler.ConsumesJson
@BootstrapContextCompatible
public final class JsonMessageHandler<T> implements MessageBodyHandler<T>, ChunkedMessageBodyReader<T>, CustomizableJsonHandler, ResponseBodyWriter<T> {

    /**
     * The JSON handler should be preferred if for any type.
     */
    public static final int ORDER = -10;

    /**
     * Starting capacity of the buffer a whole JSON response is serialised into. Jackson's
     * generator writes through an 8000 byte encoding buffer, so a response of up to that size
     * arrives in one write; with the allocator's 256 byte default that first write already forced
     * the buffer to grow. Only used for a complete body, not for the elements of a streamed
     * response: those are written to the channel uncopied, and a hint this large would hold 8 KiB
     * of capacity for every small element the client has not read yet.
     */
    private static final int WRITE_BUFFER_SIZE = 8192;

    private final JsonMapper jsonMapper;
    /**
     * The type this handler is specialized for, see {@link #createSpecific(Argument)}, or
     * {@code null}. Only this exact argument is read and written with {@link #specificMapper}; a
     * specialized mapper may not be called with any other type, so every other argument goes
     * through the general {@link #jsonMapper}.
     */
    private final @Nullable Argument<?> specificType;
    private final JsonMapper specificMapper;
    /**
     * Releases the buffers of the runtime that a Reactor input of {@link #readChunked} discards,
     * or {@code null} if the runtime has none that need it.
     */
    private final @Nullable ForeignBufferReleaser bufferReleaser;

    public JsonMessageHandler(JsonMapper jsonMapper) {
        this(jsonMapper, (ForeignBufferReleaser) null);
    }

    /**
     * @param jsonMapper     The mapper
     * @param bufferReleaser Releases the buffers of the runtime that a Reactor input of
     *                       {@link #readChunked} discards, e.g. Netty buffers, or {@code null}
     * @since 5.3.0
     */
    @Inject
    @Internal
    public JsonMessageHandler(JsonMapper jsonMapper, @Nullable ForeignBufferReleaser bufferReleaser) {
        this(jsonMapper, null, jsonMapper, bufferReleaser);
    }

    private JsonMessageHandler(JsonMapper jsonMapper, @Nullable Argument<?> specificType, JsonMapper specificMapper, @Nullable ForeignBufferReleaser bufferReleaser) {
        this.jsonMapper = jsonMapper;
        this.specificType = specificType;
        this.specificMapper = specificMapper;
        this.bufferReleaser = bufferReleaser;
    }

    /**
     * @return The releaser of the buffers of the runtime, or {@code null}
     */
    @Nullable ForeignBufferReleaser bufferReleaser() {
        return bufferReleaser;
    }

    /**
     * The elements a piece reader reads from the buffers of a body, which are shared with the
     * values, not copied.
     *
     * @param input          The buffers of the body
     * @param reader         The reader
     * @param bufferReleaser Releases a buffer of the runtime that the input discards, or
     *                       {@code null}
     * @param <E>            The type of an element
     * @return The publisher of the elements
     */
    static <E> Publisher<E> publisherOfBuffers(Publisher<ByteBuffer<?>> input, PieceReader<E> reader, @Nullable ForeignBufferReleaser bufferReleaser) {
        if (bufferReleaser == null) {
            return PieceReaders.publisherOfBuffers(input, reader, SharedReadBuffer::adapt);
        }
        return PieceReaders.publisherOfBuffers(input, reader, SharedReadBuffer::adapt, bufferReleaser::release);
    }

    /**
     * Get the json mapper used by this handler.
     *
     * @return The mapper
     */
    public JsonMapper getJsonMapper() {
        return jsonMapper;
    }

    @Override
    public boolean isReadable(Argument<T> type, @Nullable MediaType mediaType) {
        return mediaType != null && mediaType.matchesAllOrWildcardOrExtension(MediaType.EXTENSION_JSON);
    }

    private static CodecException decorateRead(Argument<?> type, IOException e) {
        return new CodecException("Error decoding JSON stream for type [" + type.getName() + "]: " + e.getMessage(), e);
    }

    @Override
    public JsonMessageHandler<T> createSpecific(Argument<T> type) {
        return new JsonMessageHandler<>(jsonMapper, type, jsonMapper.createSpecific(type), bufferReleaser);
    }

    @Override
    public JsonMessageHandler<T> createSpecificReader(Argument<T> type) {
        return createSpecific(type);
    }

    private JsonMapper mapper(Argument<?> type) {
        return type == specificType ? specificMapper : jsonMapper;
    }

    @Override
    @Nullable
    public T read(Argument<T> type, @Nullable MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
        T decoded;
        try {
            decoded = mapper(type).readValue(byteBuffer, type);
        } catch (IOException e) {
            throw decorateRead(type, e);
        }
        if (byteBuffer instanceof ReferenceCounted rc) {
            rc.release();
        }
        return decoded;
    }

    /**
     * Read one value from the bytes of a value of a JSON array or stream, see
     * {@link JsonPieceReader}.
     *
     * @param type  The type
     * @param value The bytes of the value, which are consumed
     * @return The value
     * @throws CodecException If the value cannot be decoded
     */
    @Nullable
    T readValue(Argument<T> type, ReadBuffer value) throws CodecException {
        try {
            return mapper(type).readValue(value, type);
        } catch (IOException e) {
            throw decorateRead(type, e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>A top-level JSON array is unwrapped: each of its elements is read as the given type,
     * unless the type is a collection, e.g. {@code Publisher<List<T>>}, in which case the whole
     * input is read as one value.</p>
     */
    @Override
    public Publisher<T> readChunked(Argument<T> type, @Nullable MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
        JsonChunkedProcessor processor = new JsonChunkedProcessor();
        if (Iterable.class.isAssignableFrom(type.getType())) {
            // Publisher<List<T>> is parsed as a single item of type List
            processor.counter.noTokenization();
        } else {
            // Publisher<T> is unwrapped
            processor.counter.unwrapTopLevelArray();
        }
        return publisherOfBuffers(input, new JsonPieceReader<>(processor, value -> readValue(type, value)), bufferReleaser);
    }

    /**
     * {@inheritDoc}
     *
     * <p>A top-level JSON array is always unwrapped: each of its elements is read as the given
     * type, a collection too, e.g. {@code [[1,2],[3,4]]} as two lists.</p>
     */
    @Override
    public Publisher<T> readChunked(Argument<T> type, @Nullable MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input, long maxElementSize) {
        // the buffers of the input are shared with the values, not copied
        return publisherOfBuffers(input, openPieceReader(type, mediaType, httpHeaders, maxElementSize), bufferReleaser);
    }

    /**
     * {@inheritDoc}
     *
     * <p>A top-level JSON array is always unwrapped: each of its elements is read as the given
     * type, a collection too, e.g. {@code [[1,2],[3,4]]} as two lists. Any other JSON is read as
     * a stream of values.</p>
     */
    @Override
    public PieceReader<T> openPieceReader(Argument<T> type, @Nullable MediaType mediaType, Headers httpHeaders, long maxElementSize) {
        JsonChunkedProcessor processor = new JsonChunkedProcessor(maxElementSize);
        processor.counter.unwrapTopLevelArray();
        return new JsonPieceReader<>(processor, value -> readValue(type, value));
    }

    @Override
    @Nullable
    public T read(Argument<T> type, @Nullable MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        try {
            return mapper(type).readValue(inputStream, type);
        } catch (IOException e) {
            throw decorateRead(type, e);
        }
    }

    @Override
    public boolean isWriteable(Argument<T> type, @Nullable MediaType mediaType) {
        return mediaType != null && mediaType.matchesAllOrWildcardOrExtension(MediaType.EXTENSION_JSON);
    }

    /**
     * A failure of the mapper while writing is an encoding failure, whether it is an
     * {@link IOException} or unchecked, like the exceptions of Jackson 3.
     */
    static CodecException decorateWrite(Object object, Exception e) {
        return new CodecException("Error encoding object [" + object + "] to JSON: " + e.getMessage(), e);
    }

    @Override
    public void writeTo(Argument<T> type, @Nullable MediaType mediaType, T object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        outgoingHeaders.set(HttpHeaders.CONTENT_TYPE, mediaType != null ? mediaType : MediaType.APPLICATION_JSON_TYPE);
        try {
            writeValue(type, mediaType, outgoingHeaders, object, outputStream);
        } catch (CodecException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw decorateWrite(object, e);
        }
    }

    @Override
    public ByteBodyHttpResponse<?> write(ByteBodyFactory bodyFactory, HttpRequest<?> request, MutableHttpResponse<T> httpResponse, Argument<T> type, MediaType mediaType, T object) throws CodecException {
        httpResponse.getHeaders().contentTypeIfMissing(mediaType);
        try {
            return ByteBodyHttpResponseWrapper.wrap(httpResponse, bodyFactory.buffer(WRITE_BUFFER_SIZE, s -> mapper(type).writeValue(s, object)));
        } catch (CodecException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw decorateWrite(object, e);
        }
    }

    @Override
    public CloseableByteBody writePiece(ByteBodyFactory bodyFactory, HttpRequest<?> request, HttpResponse<?> response, Argument<T> type, MediaType mediaType, T object) throws CodecException {
        try {
            return bodyFactory.buffer(s -> writeValue(type, mediaType, response.getHeaders(), object, s));
        } catch (CodecException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw decorateWrite(object, e);
        }
    }

    @Override
    public PieceWriter<T> openPieceWriter(ByteBodyFactory bodyFactory, HttpRequest<?> request, HttpResponse<?> response, Argument<T> type, MediaType mediaType) throws CodecException {
        if (type.getType() == Object.class) {
            // a piece of undeclared type may be an already serialized document that writeValue
            // passes through unchanged, so these pieces are written one at a time
            return ResponseBodyWriter.super.openPieceWriter(bodyFactory, request, response, type, mediaType);
        }
        return new JsonPieceWriter<>(bodyFactory, mapper(type), type);
    }

    /**
     * Write a single value, either through unchanged if it is an already serialized JSON document,
     * or using the {@link JsonMapper} with the declared type.
     */
    private void writeValue(Argument<T> type, @Nullable MediaType mediaType, Headers headers, T object, OutputStream outputStream) throws IOException {
        if (type.getType() == Object.class && object instanceof CharSequence charSequence) {
            // the value is already a JSON document, write it through unchanged
            outputStream.write(charSequence.toString().getBytes(MessageBodyWriter.findCharset(mediaType, headers).orElse(StandardCharsets.UTF_8)));
        } else {
            mapper(type).writeValue(outputStream, type, object);
        }
    }

    @Override
    public CustomizableJsonHandler customize(JsonFeatures jsonFeatures) {
        return new JsonMessageHandler<>(jsonMapper.cloneWithFeatures(jsonFeatures), bufferReleaser);
    }

    /**
     * A {@link Produces} with JSON supported types.
     */
    @Documented
    @Retention(RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    @Produces({
        MediaType.APPLICATION_JSON,
        MediaType.TEXT_JSON,
        MediaType.APPLICATION_HAL_JSON,
        MediaType.APPLICATION_JSON_GITHUB,
        MediaType.APPLICATION_JSON_FEED,
        MediaType.APPLICATION_JSON_PROBLEM,
        MediaType.APPLICATION_JSON_PATCH,
        MediaType.APPLICATION_JSON_MERGE_PATCH,
        MediaType.APPLICATION_JSON_SCHEMA,
        MediaType.APPLICATION_SCIM_JSON
    })
    public @interface ProducesJson {
    }

    /**
     * A {@link Consumes} with JSON supported types.
     */
    @Documented
    @Retention(RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    @Consumes({
        MediaType.APPLICATION_JSON,
        MediaType.TEXT_JSON,
        MediaType.APPLICATION_HAL_JSON,
        MediaType.APPLICATION_JSON_GITHUB,
        MediaType.APPLICATION_JSON_FEED,
        MediaType.APPLICATION_JSON_PROBLEM,
        MediaType.APPLICATION_JSON_PATCH,
        MediaType.APPLICATION_JSON_MERGE_PATCH,
        MediaType.APPLICATION_JSON_SCHEMA,
        MediaType.APPLICATION_SCIM_JSON
    })
    public @interface ConsumesJson {
    }


}
