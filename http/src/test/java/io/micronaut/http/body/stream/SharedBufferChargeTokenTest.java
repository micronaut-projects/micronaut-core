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

import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;

import java.io.IOException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Timeout(30)
class SharedBufferChargeTokenTest {
    private static final BufferConsumer.Upstream UPSTREAM = bytes -> { };
    private static final ReadBufferFactory FACTORY = ReadBufferFactory.getJdkFactory();

    @Test
    void cancellationReturnsQueuedChargeOnce() {
        Fixture f = new Fixture();
        f.reader.add(FACTORY.adapt(new byte[60]));
        f.subscriber.cancel();
        f.subscriber.cancel();
        f.reader.complete();
        f.reader.error(new IOException("late"));
        f.reader.add(FACTORY.adapt(new byte[60]));
        f.assertBudgetReturned();
    }

    @Test
    void deliveryAndTerminationReturnChargeOnce() {
        for (boolean fail : new boolean[]{false, true}) {
            Fixture f = new Fixture();
            f.reader.add(FACTORY.adapt(new byte[60]));
            if (fail) {
                f.reader.error(new IOException("test"));
            } else {
                f.reader.complete();
            }
            f.subscriber.request(1);
            f.subscriber.cancel();
            f.reader.add(FACTORY.adapt(new byte[60]));
            f.assertBudgetReturned();
        }
    }

    @Test
    void rejectedEmissionIsRefundedBeforeThePublisherIsSubscribed() {
        Fixture f = new Fixture(false);
        f.reader.complete();
        f.reader.add(FACTORY.adapt(new byte[60]));
        f.assertBudgetReturned();
    }

    @Test
    void cancellationRacingWithAddReturnsEachChargeOnce() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            CyclicBarrier start = new CyclicBarrier(3);
            for (int i = 0; i < 300; i++) {
                Fixture f = new Fixture();
                var adding = executor.submit(() -> {
                    start.await();
                    f.reader.add(FACTORY.adapt(new byte[60]));
                    return null;
                });
                var cancelling = executor.submit(() -> {
                    start.await();
                    f.subscriber.cancel();
                    return null;
                });
                start.await();
                adding.get();
                cancelling.get();
                f.assertBudgetReturned();
            }
        }
    }

    @Test
    void cancellationDuringDeliveryDoesNotSkipTheNextSubscriber() {
        Fixture f = new Fixture();
        AtomicInteger deliveries = new AtomicInteger();
        f.buffer.reserve0();
        f.buffer.reserve0();
        f.buffer.subscribe0(new CallbackReader(f.subscriber::cancel), UPSTREAM);
        f.buffer.subscribe0(f.reader, UPSTREAM);
        f.buffer.subscribe0(new CallbackReader(deliveries::incrementAndGet), UPSTREAM);

        f.buffer.add(FACTORY.adapt(new byte[60]));
        assertEquals(1, deliveries.get());
        f.assertBudgetReturned();
    }

    private record CallbackReader(Runnable callback) implements BufferConsumer {
        @Override
        public void add(ReadBuffer rb) {
            rb.close();
            callback.run();
        }

        @Override
        public void complete() {
        }

        @Override
        public void error(Throwable e) {
            throw new AssertionError(e);
        }
    }

    private static final class Fixture {
        final BaseSharedBuffer buffer = new BaseSharedBuffer(FACTORY, BodySizeLimits.UNLIMITED, UPSTREAM) {
            @Override
            protected void removeSubscriber(BufferConsumer subscriber) {
                // These tests serialize active subscriptions on the calling thread.
                removeSubscriber0(subscriber);
            }
        };
        final SizeLimitTracker.TrackerPair trackers = SizeLimitTracker.notThreadSafe(new BodySizeLimits(Long.MAX_VALUE, 100)).makeBothAtomic();
        final BaseSharedBuffer.AsFlux reader = new BaseSharedBuffer.AsFlux(buffer);
        final BaseSubscriber<ReadBuffer> subscriber = new BaseSubscriber<>() {
            @Override
            protected void hookOnSubscribe(Subscription subscription) {
                // Deliberately leave bytes queued in the sink.
            }

            @Override
            protected void hookOnNext(ReadBuffer value) {
                value.close();
            }

            @Override
            protected void hookOnError(Throwable throwable) {
            }
        };

        Fixture() {
            this(true);
        }

        Fixture(boolean subscribe) {
            buffer.addSizeLimitTrackers(trackers);
            if (subscribe) {
                reader.asFlux(UPSTREAM).subscribe(subscriber);
            }
        }

        void assertBudgetReturned() {
            assertNull(trackers.bufferedSize().add(100));
            assertNotNull(trackers.bufferedSize().add(1));
        }
    }
}
