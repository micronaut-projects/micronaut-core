package io.micronaut.json.body;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.json.JsonMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The elements of a JSON body read one at a time with a limit: every top-level value, each
 * decoded with its full type, a collection too, and the buffer of an element that does not
 * decode is released.
 */
class JsonChunkedElementsTest {

    private static final JsonMapper MAPPER = JsonMapper.createDefault();
    private static final Argument<List<Integer>> NUMBERS = Argument.listOf(Integer.class);

    @Test
    void eachElementOfAnArrayIsACollection() {
        JsonMessageHandler<List<Integer>> handler = new JsonMessageHandler<>(MAPPER);
        ByteBuf input = buffer("[[1,2],[3,4]]");

        List<List<Integer>> lists = Flux.from(handler.readChunked(NUMBERS, MediaType.APPLICATION_JSON_TYPE, new SimpleHttpHeaders(), Flux.<ByteBuffer<?>>just(wrap(input)), 1024))
            .collectList()
            .block();

        assertEquals(List.of(List.of(1, 2), List.of(3, 4)), lists);
        assertEquals(0, input.refCnt());
    }

    @Test
    void eachValueOfAStreamIsACollection() {
        JsonStreamMessageHandler<List<Integer>> handler = new JsonStreamMessageHandler<>(MAPPER);
        ByteBuf input = buffer("[1,2]\n[3,4]\n");

        List<List<Integer>> lists = Flux.from(handler.readChunked(NUMBERS, MediaType.APPLICATION_JSON_STREAM_TYPE, new SimpleHttpHeaders(), Flux.<ByteBuffer<?>>just(wrap(input)), 1024))
            .collectList()
            .block();

        assertEquals(List.of(List.of(1, 2), List.of(3, 4)), lists);
        assertEquals(0, input.refCnt());
    }

    @Test
    void aCollectionReadWithoutALimitIsStillTheWholeArray() {
        JsonMessageHandler<List<Integer>> handler = new JsonMessageHandler<>(MAPPER);
        ByteBuf input = buffer("[1,2,3]");

        List<List<Integer>> lists = Flux.from(handler.readChunked(NUMBERS, MediaType.APPLICATION_JSON_TYPE, new SimpleHttpHeaders(), Flux.<ByteBuffer<?>>just(wrap(input))))
            .collectList()
            .block();

        // a Publisher<List<T>> body: one list
        assertEquals(List.of(List.of(1, 2, 3)), lists);
        assertEquals(0, input.refCnt());
    }

    @Test
    void theBufferOfAnElementThatDoesNotDecodeIsReleased() {
        JsonMessageHandler<Map> handler = new JsonMessageHandler<>(MAPPER);
        ByteBuf input = buffer("[{\"a\":1},[1],{\"b\":2}]");

        assertThrows(RuntimeException.class, () -> Flux.from(handler.readChunked(Argument.of(Map.class), MediaType.APPLICATION_JSON_TYPE, new SimpleHttpHeaders(), Flux.<ByteBuffer<?>>just(wrap(input)), 1024))
            .collectList()
            .block());

        assertEquals(0, input.refCnt());
    }

    @Test
    void theBufferOfAStreamValueThatDoesNotDecodeIsReleased() {
        JsonStreamMessageHandler<Map> handler = new JsonStreamMessageHandler<>(MAPPER);
        ByteBuf input = buffer("{\"a\":1}\n[1]\n{\"b\":2}\n");

        assertThrows(RuntimeException.class, () -> Flux.from(handler.readChunked(Argument.of(Map.class), MediaType.APPLICATION_JSON_STREAM_TYPE, new SimpleHttpHeaders(), Flux.<ByteBuffer<?>>just(wrap(input)), 1024))
            .collectList()
            .block());

        assertEquals(0, input.refCnt());
    }

    private static ByteBuf buffer(String text) {
        return Unpooled.copiedBuffer(text, StandardCharsets.UTF_8);
    }

    private static ByteBuffer<?> wrap(ByteBuf buf) {
        return NettyByteBufferFactory.DEFAULT.wrap(buf);
    }
}
