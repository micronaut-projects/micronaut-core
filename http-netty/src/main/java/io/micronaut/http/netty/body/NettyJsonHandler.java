package io.micronaut.http.netty.body;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.MessageBodyHandler;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.body.PiecewiseMessageBodyReader;
import io.micronaut.http.codec.CodecException;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.body.JsonMessageHandler;
import io.netty.buffer.ByteBuf;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.InputStream;
import java.io.OutputStream;

@Singleton
@Internal
@Replaces(JsonMessageHandler.class)
class NettyJsonHandler<T> implements MessageBodyHandler<T>, PiecewiseMessageBodyReader<T> {
    private final JsonMessageHandler<T> jsonMessageHandler;

    public NettyJsonHandler(JsonMapper jsonMapper) {
        this(new JsonMessageHandler<>(jsonMapper));
    }

    private NettyJsonHandler(JsonMessageHandler<T> jsonMessageHandler) {
        this.jsonMessageHandler = jsonMessageHandler;
    }

    @Override
    public Publisher<T> readPiecewise(Argument<T> type, MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
        JsonPiecewiseProcessor processor = new JsonPiecewiseProcessor();
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
    public MessageBodyWriter<T> createSpecific(Argument<T> type) {
        return new NettyJsonHandler<>((JsonMessageHandler<T>) jsonMessageHandler.createSpecific(type));
    }

    @Override
    public boolean isBlocking() {
        return jsonMessageHandler.isBlocking();
    }
}
