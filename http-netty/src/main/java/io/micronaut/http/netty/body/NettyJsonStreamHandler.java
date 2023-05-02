package io.micronaut.http.netty.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.body.PiecewiseMessageBodyReader;
import io.micronaut.http.codec.CodecException;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.body.JsonMessageHandler;
import io.netty.buffer.ByteBuf;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.InputStream;
import java.util.List;

@Internal
@Singleton
@Produces(MediaType.APPLICATION_JSON_STREAM)
@Consumes(MediaType.APPLICATION_JSON_STREAM)
class NettyJsonStreamHandler<T> implements MessageBodyReader<T>, PiecewiseMessageBodyReader<T> {
    private final JsonMessageHandler<T> jsonMessageHandler;

    public NettyJsonStreamHandler(JsonMapper jsonMapper) {
        this(new JsonMessageHandler<>(jsonMapper));
    }

    private NettyJsonStreamHandler(JsonMessageHandler<T> jsonMessageHandler) {
        this.jsonMessageHandler = jsonMessageHandler;
    }

    @Override
    public boolean isReadable(Argument<T> type, MediaType mediaType) {
        return mediaType.matches(MediaType.APPLICATION_JSON_STREAM_TYPE);
    }

    @Override
    public T read(Argument<T> type, MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
        if (!type.getType().isAssignableFrom(List.class)) {
            throw new UnsupportedOperationException("Can only read json-stream to a Publisher or list type");
        }
        //noinspection unchecked
        return (T) readPiecewise((Argument<T>) type.getFirstTypeVariable().orElse(type), mediaType, httpHeaders, Flux.just(byteBuffer)).collectList().block();
    }

    @Override
    public T read(Argument<T> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        throw new UnsupportedOperationException("Reading from InputStram is not supported for json-stream");
    }

    @Override
    public Flux<T> readPiecewise(Argument<T> type, MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
        JsonPiecewiseProcessor processor = new JsonPiecewiseProcessor();
        return processor.process(Flux.from(input).map(bb -> {
            if (!(bb.asNativeBuffer() instanceof ByteBuf buf)) {
                throw new IllegalArgumentException("Only netty buffers are supported");
            }
            return buf;
        })).map(bb -> jsonMessageHandler.read(type, mediaType, httpHeaders, bb));
    }
}
