package io.micronaut.http.netty.body;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.DelegateByteBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The JSON values of an input whose buffers are not Netty buffers, e.g. the buffers of a server
 * that is not a Netty server.
 */
class JsonChunkedNonNettyBufferTest {

    private static final JsonMapper MAPPER = JsonMapper.createDefault();

    @Test
    void theJsonHandlerReadsTheValuesOfBuffersThatAreNotNettyBuffers() {
        NettyJsonHandler<Map> handler = new NettyJsonHandler<>(MAPPER);
        CountedBuffer first = buffer("[{\"a\":1},{\"b\"");
        CountedBuffer second = buffer(":2}]");

        List<Map> values = Flux.from(handler.readChunked(Argument.of(Map.class), MediaType.APPLICATION_JSON_TYPE, new SimpleHttpHeaders(), Flux.<ByteBuffer<?>>just(first, second), 1024))
            .collectList()
            .block();

        assertEquals(List.of(Map.of("a", 1), Map.of("b", 2)), values);
        // copied into Netty buffers, and released
        assertEquals(1, first.released);
        assertEquals(1, second.released);
    }

    @Test
    void theJsonStreamHandlerReadsTheValuesOfBuffersThatAreNotNettyBuffers() {
        NettyJsonStreamHandler<Map> handler = new NettyJsonStreamHandler<>(MAPPER);
        CountedBuffer first = buffer("{\"a\":1}\n{\"b\"");
        CountedBuffer second = buffer(":2}\n");

        List<Map> values = handler.readChunked(Argument.of(Map.class), MediaType.APPLICATION_JSON_STREAM_TYPE, new SimpleHttpHeaders(), Flux.<ByteBuffer<?>>just(first, second), 1024)
            .collectList()
            .block();

        assertEquals(List.of(Map.of("a", 1), Map.of("b", 2)), values);
        assertEquals(1, first.released);
        assertEquals(1, second.released);
    }

    @Test
    void theValuesStartAtTheReaderIndexOfABuffer() {
        NettyJsonHandler<Map> handler = new NettyJsonHandler<>(MAPPER);
        CountedBuffer buffer = buffer("xx[{\"a\":1}]");
        // the first two bytes were read before
        buffer.readerIndex(2);

        List<Map> values = Flux.from(handler.readChunked(Argument.of(Map.class), MediaType.APPLICATION_JSON_TYPE, new SimpleHttpHeaders(), Flux.<ByteBuffer<?>>just(buffer), 1024))
            .collectList()
            .block();

        // every readable byte, up to the last
        assertEquals(List.of(Map.of("a", 1)), values);
        assertEquals(1, buffer.released);
    }

    private static CountedBuffer buffer(String text) {
        return new CountedBuffer(ByteArrayBufferFactory.INSTANCE.wrap(text.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * A buffer of a byte array that counts its releases.
     */
    private static final class CountedBuffer extends DelegateByteBuffer<byte[]> implements ReferenceCounted {
        int released;

        CountedBuffer(ByteBuffer<byte[]> delegate) {
            super(delegate);
        }

        @Override
        public ReferenceCounted retain() {
            return this;
        }

        @Override
        public boolean release() {
            released++;
            return true;
        }
    }
}
