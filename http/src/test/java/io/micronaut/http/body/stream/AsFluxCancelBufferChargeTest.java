/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.body.stream;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import org.reactivestreams.Subscription;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A reader of a split body that cancels stays subscribed to the shared buffer. The data that
 * follows must not count against the buffer limit on its behalf: nothing would ever return it, and
 * the other reader of the body would be failed for exceeding a limit it stays well below.
 */
class AsFluxCancelBufferChargeTest {

    private static final BufferConsumer.Upstream NO_UPSTREAM = bytesConsumed -> {
    };

    @Test
    @Timeout(10)
    void cancelledReaderDoesNotUseUpTheBufferLimit() {
        ByteBodyFactory bbf = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        ByteBodyFactory.StreamingBody streamingBody = bbf.createStreamingBody(new BodySizeLimits(Long.MAX_VALUE, 10), NO_UPSTREAM);
        List<Throwable> errors = new ArrayList<>();
        List<String> received = new ArrayList<>();
        try (CloseableByteBody reading = streamingBody.rootBody();
             CloseableByteBody cancelling = reading.split(ByteBody.SplitBackpressureMode.FASTEST)) {
            BaseSubscriber<ReadBuffer> cancelled = new BaseSubscriber<>() {
                @Override
                protected void hookOnSubscribe(Subscription subscription) {
                    // no demand: what arrives before the cancel stays queued
                }

                @Override
                protected void hookOnNext(ReadBuffer value) {
                    value.close();
                }
            };
            Flux.from(cancelling.toReadBufferPublisher()).subscribe(cancelled);
            Flux.from(reading.toReadBufferPublisher())
                .subscribe(buf -> received.add(buf.toString(StandardCharsets.UTF_8)), errors::add);

            // queued for the reader that is about to cancel, and returned to the limit by the cancel
            streamingBody.sharedBuffer().add(bbf.readBufferFactory().copyOf("abcd", StandardCharsets.UTF_8));
            cancelled.cancel();
            for (int i = 0; i < 5; i++) {
                streamingBody.sharedBuffer().add(bbf.readBufferFactory().copyOf("abcd", StandardCharsets.UTF_8));
            }
        }

        assertEquals(List.of(), errors);
        assertEquals(6, received.size());
    }
}
