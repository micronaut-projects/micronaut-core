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
package io.micronaut.core.async.publisher;

import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The elements that {@link Publishers#bufferNow} discards are closed.
 */
class PublishersBufferNowTest {

    @Test
    void anElementBufferedByTheSourceIsClosedOnCancellation() {
        Item second = new Item();
        // the second element completes first, so the source holds it until the first one completes
        Flux<Item> source = Flux.range(0, 2)
            .flatMapSequential(i -> i == 0 ? Mono.<Item>never() : Mono.just(second));

        Disposable subscription = Flux.from(Publishers.bufferNow(source)).subscribe();
        subscription.dispose();

        assertTrue(second.closed.get());
    }

    @Test
    void anElementBufferedByTheSinkIsClosedOnCancellation() {
        Item item = new Item();
        Sinks.Many<Item> source = Sinks.many().unicast().onBackpressureBuffer();
        Flux<Item> buffered = Flux.from(Publishers.bufferNow(source.asFlux()));
        source.tryEmitNext(item);

        buffered.subscribe(new reactor.core.publisher.BaseSubscriber<>() {
            @Override
            protected void hookOnSubscribe(org.reactivestreams.Subscription subscription) {
                subscription.cancel();
            }
        });

        assertTrue(item.closed.get());
    }

    private static final class Item implements Closeable {
        final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public void close() {
            closed.set(true);
        }
    }
}
