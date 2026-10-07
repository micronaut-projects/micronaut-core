package io.micronaut.http.netty.body;

import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.ChunkedMessageBodyReader;
import io.micronaut.http.body.PieceReader;
import io.micronaut.http.body.stream.PieceReaders;
import io.micronaut.http.codec.CodecException;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.body.JsonMessageHandler;
import io.netty.buffer.ByteBuf;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Benchmark baseline: the reading of the removed {@code NettyJsonHandler} and
 * {@code NettyJsonStreamHandler}, on the copies of their Netty processor, to compare the readers
 * of json-core with.
 *
 * @param <T> The type
 */
public final class NettyJsonBaseline<T> implements ChunkedMessageBodyReader<T> {
    private final JsonMessageHandler<T> json;
    private final boolean stream;

    /**
     * @param mapper The mapper
     * @param stream Whether this reads a JSON stream, else JSON whose top-level array is unwrapped
     */
    public NettyJsonBaseline(JsonMapper mapper, boolean stream) {
        this.json = new JsonMessageHandler<>(mapper);
        this.stream = stream;
    }

    @Override
    public boolean isReadable(Argument<T> type, MediaType mediaType) {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T read(Argument<T> type, MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
        if (!stream) {
            return json.read(type, mediaType, httpHeaders, byteBuffer);
        }
        Argument<T> elementType = (Argument<T>) type.getFirstTypeVariable().orElse(type);
        List<ByteBuffer<?>> values = new ArrayList<>();
        JsonChunkedProcessor processor = new JsonChunkedProcessor();
        ByteBuf content = JsonChunkedProcessor.nettyBuffer(byteBuffer);
        try {
            processor.feed(content, values::add);
            processor.finish(values::add);
        } catch (IOException e) {
            values.forEach(JsonChunkedProcessor::release);
            throw new CodecException("Error decoding JSON stream for type [" + elementType.getName() + "]: " + e.getMessage(), e);
        } finally {
            content.release();
            processor.discard();
        }
        List<Object> elements = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            try {
                elements.add(JsonChunkedProcessor.readReleasing(values.get(i), value -> json.read(elementType, mediaType, httpHeaders, value)));
            } catch (RuntimeException e) {
                values.subList(i + 1, values.size()).forEach(JsonChunkedProcessor::release);
                throw e;
            }
        }
        return (T) elements;
    }

    @Override
    public T read(Argument<T> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        return json.read(type, mediaType, httpHeaders, inputStream);
    }

    @Override
    public Publisher<T> readChunked(Argument<T> type, MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
        JsonChunkedProcessor processor = new JsonChunkedProcessor();
        if (!stream) {
            if (Iterable.class.isAssignableFrom(type.getType())) {
                processor.counter.noTokenization();
            } else {
                processor.counter.unwrapTopLevelArray();
            }
        }
        return PieceReaders.publisherOfBuffers(input, new JsonPieceReader<>(processor, value -> json.read(type, mediaType, httpHeaders, value)), JsonPieceReader::adapt);
    }

    @Override
    public Publisher<T> readChunked(Argument<T> type, MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input, long maxElementSize) {
        return PieceReaders.publisherOfBuffers(input, openPieceReader(type, mediaType, httpHeaders, maxElementSize), JsonPieceReader::adapt);
    }

    @Override
    public PieceReader<T> openPieceReader(Argument<T> type, MediaType mediaType, Headers httpHeaders, long maxElementSize) {
        JsonChunkedProcessor processor = new JsonChunkedProcessor(maxElementSize);
        if (!stream) {
            processor.counter.unwrapTopLevelArray();
        }
        return new JsonPieceReader<>(processor, value -> json.read(type, mediaType, httpHeaders, value));
    }
}
