package io.micronaut.json.body;

import io.micronaut.core.io.buffer.ReadBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonChunkedProcessorTest {

    @Test
    void cancellingOnAnEmittedValueDoesNotReleaseItAgain() {
        // one input buffer with two complete values and the start of a third: the subscriber
        // cancels on the first value, while the rest of the buffer is still processed
        ByteBuf input = Unpooled.copiedBuffer("{\"a\":1} {\"b\":2} {\"c\"", StandardCharsets.UTF_8);
        int[] refCntAfterCancel = new int[1];
        List<String> received = new ArrayList<>();
        JsonChunkedFlux.process(new JsonChunkedProcessor(), Flux.just(input)).subscribe(new Subscriber<ReadBuffer>() {
            private Subscription subscription;

            @Override
            public void onSubscribe(Subscription s) {
                subscription = s;
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ReadBuffer buffer) {
                // consumes the value, which releases it
                received.add(buffer.toString(StandardCharsets.UTF_8));
                // the value is emitted while the input is processed: cancelling here releases
                // what is buffered, not the value that was just emitted
                subscription.cancel();
                refCntAfterCancel[0] = input.refCnt();
            }

            @Override
            public void onError(Throwable t) {
                // not expected: the input is valid JSON
            }

            @Override
            public void onComplete() {
                // not expected: the subscription is cancelled on the first value
            }
        });
        assertEquals(List.of("{\"a\":1}"), received);
        assertEquals(1, refCntAfterCancel[0], "the input is not released while it is processed");
        assertEquals(0, input.refCnt(), "the partial value buffered after the cancellation is released");
    }

    @Test
    void everyValueIsEmittedAndReleasedAtTheEnd() {
        ByteBuf first = Unpooled.copiedBuffer("{\"a\":1} {\"b\"", StandardCharsets.UTF_8);
        ByteBuf second = Unpooled.copiedBuffer(":2} {\"c\":3}", StandardCharsets.UTF_8);
        List<String> received = new ArrayList<>();
        JsonChunkedFlux.process(new JsonChunkedProcessor(), Flux.just(first, second)).doOnNext(buffer -> {
            received.add(buffer.toString(StandardCharsets.UTF_8));
        }).blockLast();
        assertEquals(List.of("{\"a\":1}", "{\"b\":2}", "{\"c\":3}"), received);
        assertEquals(0, first.refCnt());
        assertEquals(0, second.refCnt());
    }
}
