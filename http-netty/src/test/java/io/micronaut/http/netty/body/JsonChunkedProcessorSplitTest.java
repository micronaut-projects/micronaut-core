package io.micronaut.http.netty.body;

import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.json.JsonSyntaxException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Streamed JSON splitting: pieces of a value must not be recopied, values must be split on
 * demand, and the UTF-8 guard applies to every top-level value.
 */
class JsonChunkedProcessorSplitTest {


    @Test
    void piecesOfABufferedValueAreNotConsolidated() {
        List<ByteBuf> input = new ArrayList<>();
        input.add(Unpooled.copiedBuffer("\"", StandardCharsets.UTF_8));
        for (int i = 0; i < 100; i++) {
            input.add(Unpooled.copiedBuffer("a", StandardCharsets.UTF_8));
        }
        input.add(Unpooled.copiedBuffer("\"", StandardCharsets.UTF_8));
        List<Integer> components = new ArrayList<>();
        List<String> received = new ArrayList<>();
        new JsonChunkedProcessor().process(Flux.fromIterable(input)).doOnNext(buffer -> {
            ByteBuf buf = (ByteBuf) buffer.asNativeBuffer();
            components.add(buf instanceof CompositeByteBuf c ? c.numComponents() : 1);
            received.add(buf.toString(StandardCharsets.UTF_8));
            buf.release();
        }).blockLast();
        assertEquals(List.of("\"" + "a".repeat(100) + "\""), received);
        // a consolidated composite (every 16 components) has fewer components than pieces given
        assertEquals(List.of(input.size()), components);
    }


    @Test
    void valuesAreSplitOffOnDemand() {
        ByteBuf input = Unpooled.copiedBuffer("[" + "0,".repeat(999) + "0]", StandardCharsets.UTF_8);
        JsonChunkedProcessor processor = new JsonChunkedProcessor();
        processor.counter.unwrapTopLevelArray();
        List<String> received = new ArrayList<>();
        BaseSubscriber<ByteBuffer<?>> subscriber = new BaseSubscriber<>() {
            @Override
            protected void hookOnSubscribe(Subscription subscription) {
                // no initial request, the test requests explicitly
            }

            @Override
            protected void hookOnNext(ByteBuffer<?> buffer) {
                ByteBuf buf = (ByteBuf) buffer.asNativeBuffer();
                received.add(buf.toString(StandardCharsets.UTF_8));
                buf.release();
            }
        };
        processor.process(Flux.just(input)).subscribe(subscriber);
        subscriber.request(1);
        assertEquals(List.of("0"), received);
        // every split-off value retains the input; queued values ahead of demand show up here
        int refCnt = input.refCnt();
        subscriber.cancel();
        assertEquals(1, refCnt, "values were split off ahead of demand");
    }


    @Test
    void utf16AfterLeadingWhitespaceIsRejected() {
        byte[] bytes = {0x20, 0x20, 0x20, 0x20, 0x22, 0x00, 0x22, 0x5b, 0x22, 0x00};
        assertThrows(JsonSyntaxException.class, () -> feed(bytes));
    }


    @Test
    void utf16InSecondValueIsRejected() {
        byte[] bytes = {0x7b, 0x7d, 0x7b, 0x7d, 0x22, 0x00, 0x22, 0x5b, 0x22, 0x00};
        assertThrows(JsonSyntaxException.class, () -> feed(bytes));
    }

    // control: rejected when at the start
    @Test
    void utf16AtStartIsRejected() {
        byte[] bytes = {0x22, 0x00, 0x22, 0x5b, 0x22, 0x00};
        assertThrows(JsonSyntaxException.class, () -> feed(bytes));
    }

    private static void feed(byte[] bytes) throws JsonSyntaxException {
        JsonCounter counter = new JsonCounter();
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        while (buf.isReadable()) {
            counter.feed(buf);
            counter.pollFlushedRegion();
        }
    }
}
