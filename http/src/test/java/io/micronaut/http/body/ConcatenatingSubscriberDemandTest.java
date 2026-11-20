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
package io.micronaut.http.body;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * When {@link ConcatenatingSubscriber#complete()} declines to request the next item because the
 * bytes forwarded so far are not consumed yet, the consumption that catches up must request
 * exactly one next item, however many consumption callbacks follow.
 */
class ConcatenatingSubscriberDemandTest {

    @Test
    void oneDeclinedCompletionIsAnsweredByOneRequest() {
        ByteBodyFactory bbf = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        AtomicLong requested = new AtomicLong();
        Sinks.Many<ByteBody> items = Sinks.many().unicast().onBackpressureBuffer();
        ConcatenatingSubscriber subscriber = new ConcatenatingSubscriber(bbf, ConcatenatingSubscriber.Separators.NONE);
        Flux<ByteBody> source = items.asFlux().doOnRequest(requested::addAndGet);
        source.subscribe(subscriber);
        // the connection starts the body (one item is requested) but has not consumed anything yet
        subscriber.start();
        long initial = requested.get();
        assertEquals(1, initial);

        items.tryEmitNext(bbf.adapt("0123456789".getBytes(StandardCharsets.UTF_8))).orThrow();
        // complete() of the item declined, because 0 of 10 forwarded bytes were consumed
        assertEquals(initial, requested.get());

        // the connection reports the consumption, in several callbacks
        subscriber.onBytesConsumed(10);
        subscriber.onBytesConsumed(0);
        subscriber.onBytesConsumed(0);

        assertEquals(initial + 1, requested.get(), "one declined completion was answered by several requests");
        subscriber.allowDiscard();
    }
}
