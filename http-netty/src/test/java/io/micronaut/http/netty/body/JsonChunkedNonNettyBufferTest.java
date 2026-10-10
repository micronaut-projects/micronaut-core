package io.micronaut.http.netty.body;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.DelegateByteBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.JsonSyntaxException;
import org.junit.jupiter.api.Test;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The JSON values of an input whose buffers are not Netty buffers, e.g. the buffers of a server
 * that is not a Netty server.
 */
class JsonChunkedNonNettyBufferTest {

    private static final JsonMapper MAPPER = JsonMapper.createDefault();
    /**
     * More buffers than the processing requests ahead of what it processed.
     */
    private static final int MORE_THAN_PREFETCHED = 100;

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

        List<Map> values = Flux.from(handler.readChunked(Argument.of(Map.class), MediaType.APPLICATION_JSON_STREAM_TYPE, new SimpleHttpHeaders(), Flux.<ByteBuffer<?>>just(first, second), 1024))
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

    @Test
    void invalidJsonInTheMiddleReleasesEveryBufferOnce() {
        List<CountedBuffer> buffers = new ArrayList<>();
        buffers.add(buffer("[{\"a\":1},"));
        // a stray comma, which the splitting of the array rejects
        buffers.add(buffer(",{\"b\":2},"));
        buffers.addAll(elements(MORE_THAN_PREFETCHED));
        buffers.add(buffer("]"));

        // a checked exception, which block() wraps
        RuntimeException e = assertThrows(RuntimeException.class, () -> read(buffers, Long.MAX_VALUE).collectList().block());
        assertInstanceOf(JsonSyntaxException.class, Exceptions.unwrap(e));

        assertReleasedOnce(buffers);
    }

    @Test
    void anElementThatDoesNotDecodeInTheMiddleReleasesEveryBufferOnce() {
        List<CountedBuffer> buffers = new ArrayList<>();
        buffers.add(buffer("[{\"a\":1},"));
        // mismatched brackets inside an element, which its decoding rejects
        buffers.add(buffer("{\"b\":2],"));
        buffers.addAll(elements(MORE_THAN_PREFETCHED));
        buffers.add(buffer("]"));

        assertThrows(CodecException.class, () -> read(buffers, Long.MAX_VALUE).collectList().block());

        assertReleasedOnce(buffers);
    }

    @Test
    void cancellingAfterTheFirstElementReleasesEveryBufferOnce() {
        List<CountedBuffer> buffers = new ArrayList<>();
        buffers.add(buffer("["));
        buffers.addAll(elements(MORE_THAN_PREFETCHED));
        buffers.add(buffer("{\"last\":1}]"));

        Map first = read(buffers, Long.MAX_VALUE).next().block();

        assertEquals(Map.of("a", 0), first);
        assertReleasedOnce(buffers);
    }

    @Test
    void cancellingTheJsonStreamAfterTheFirstElementReleasesEveryBufferOnce() {
        List<CountedBuffer> buffers = new ArrayList<>();
        for (int i = 0; i < MORE_THAN_PREFETCHED; i++) {
            buffers.add(buffer("{\"a\":" + i + "}\n"));
        }
        NettyJsonStreamHandler<Map> handler = new NettyJsonStreamHandler<>(MAPPER);

        Map first = Flux.from(handler.readChunked(Argument.of(Map.class), MediaType.APPLICATION_JSON_STREAM_TYPE, new SimpleHttpHeaders(), Flux.<ByteBuffer<?>>fromIterable(buffers), 1024))
            .next()
            .block();

        assertEquals(Map.of("a", 0), first);
        assertReleasedOnce(buffers);
    }

    @Test
    void anEmptyBodyHasNoElementsAndIsReleasedOnce() {
        List<CountedBuffer> buffers = List.of(buffer(""));

        assertEquals(List.of(), read(buffers, Long.MAX_VALUE).collectList().block());

        assertReleasedOnce(buffers);
    }

    @Test
    void anElementOverTheLimitReleasesEveryBufferOnce() {
        List<CountedBuffer> buffers = new ArrayList<>();
        buffers.add(buffer("[{\"a\":1},{\"b\":\"01234"));
        buffers.add(buffer("56789\"},"));
        buffers.addAll(elements(MORE_THAN_PREFETCHED));
        buffers.add(buffer("{\"last\":1}]"));

        List<Map> received = new ArrayList<>();
        assertThrows(ContentLengthExceededException.class, () -> read(buffers, 12).doOnNext(received::add).collectList().block());

        // the element before it was read
        assertEquals(List.of(Map.of("a", 1)), received);
        assertReleasedOnce(buffers);
    }

    private static Flux<Map> read(List<CountedBuffer> buffers, long maxElementSize) {
        NettyJsonHandler<Map> handler = new NettyJsonHandler<>(MAPPER);
        return Flux.from(handler.readChunked(Argument.of(Map.class), MediaType.APPLICATION_JSON_TYPE, new SimpleHttpHeaders(), Flux.<ByteBuffer<?>>fromIterable(buffers), maxElementSize));
    }

    /**
     * @return Buffers of an element each, {@code {"a":i},}: more of them than the processing
     * requests ahead, so that some are not requested when it stops
     */
    private static List<CountedBuffer> elements(int count) {
        List<CountedBuffer> buffers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            buffers.add(buffer("{\"a\":" + i + "},"));
        }
        return buffers;
    }

    private static void assertReleasedOnce(List<CountedBuffer> buffers) {
        for (int i = 0; i < buffers.size(); i++) {
            assertEquals(1, buffers.get(i).released, "the releases of buffer " + i);
        }
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
