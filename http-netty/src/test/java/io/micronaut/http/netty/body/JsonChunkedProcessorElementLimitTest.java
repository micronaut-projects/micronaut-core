package io.micronaut.http.netty.body;

import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The limit of the size of each JSON value the processor buffers: the input as a whole is not
 * limited.
 */
class JsonChunkedProcessorElementLimitTest {

    @Test
    void valuesWithinTheLimitAreEmittedWhenTheInputIsLarger() {
        ByteBuf input = buffer("[{\"a\":1},{\"b\":2},{\"c\":3}]");
        List<String> received = collect(new JsonChunkedProcessor(7), Flux.just(input));
        assertEquals(List.of("{\"a\":1}", "{\"b\":2}", "{\"c\":3}"), received);
        assertEquals(0, input.refCnt());
    }

    @Test
    void aValueLargerThanTheLimitInOneBufferFails() {
        ByteBuf input = buffer("[{\"a\":1},{\"b\":\"too large\"}]");
        List<String> received = new ArrayList<>();
        JsonChunkedProcessor processor = new JsonChunkedProcessor(10);
        processor.counter.unwrapTopLevelArray();
        Flux<String> values = processor.process(Flux.just(input)).map(buffer -> {
            ByteBuf buf = (ByteBuf) buffer.asNativeBuffer();
            try {
                return buf.toString(StandardCharsets.UTF_8);
            } finally {
                buf.release();
            }
        }).doOnNext(received::add);
        assertThrows(ContentLengthExceededException.class, values::blockLast);
        assertEquals(List.of("{\"a\":1}"), received);
        assertEquals(0, input.refCnt());
    }

    @Test
    void aValueLargerThanTheLimitFailsBeforeItIsBufferedWhole() {
        ByteBuf first = buffer("[{\"a\":\"0123456789");
        ByteBuf second = buffer("0123456789");
        ByteBuf third = buffer("\"}]");
        JsonChunkedProcessor processor = new JsonChunkedProcessor(16);
        processor.counter.unwrapTopLevelArray();
        Flux<ByteBuf> input = Flux.just(first, second, third).doOnDiscard(ByteBuf.class, ByteBuf::release);
        assertThrows(ContentLengthExceededException.class, () -> processor.process(input).blockLast());
        assertEquals(0, first.refCnt());
        assertEquals(0, second.refCnt());
        if (third.refCnt() > 0) {
            // not requested after the failure
            third.release();
        }
    }

    private static List<String> collect(JsonChunkedProcessor processor, Flux<ByteBuf> input) {
        processor.counter.unwrapTopLevelArray();
        List<String> received = new ArrayList<>();
        processor.process(input).doOnNext(buffer -> {
            ByteBuf buf = (ByteBuf) buffer.asNativeBuffer();
            received.add(buf.toString(StandardCharsets.UTF_8));
            buf.release();
        }).blockLast();
        return received;
    }

    private static ByteBuf buffer(String text) {
        return Unpooled.copiedBuffer(text, StandardCharsets.UTF_8);
    }
}
