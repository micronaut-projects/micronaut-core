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
import reactor.core.publisher.Hooks;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CancelledSubscriberAccountingTest {
    @Test
    void cancellationReachesUpstreamWhenSubscriberRemovalThrows() {
        List<String> calls = new ArrayList<>();
        var failure = new IllegalStateException("removal failed");
        BufferConsumer.Upstream upstream = new BufferConsumer.Upstream() {
            @Override
            public void onBytesConsumed(long bytes) {
                // nothing to do
            }

            @Override
            public void allowDiscard() {
                calls.add("discard");
            }

            @Override
            public void disregardBackpressure() {
                calls.add("backpressure");
            }
        };
        var buffer = new BaseSharedBuffer(ReadBufferFactory.getJdkFactory(), BodySizeLimits.UNLIMITED, upstream) {
            @Override
            protected void removeSubscriber(BufferConsumer subscriber) {
                throw failure;
            }
        };
        var reader = new BaseSharedBuffer.AsFlux(buffer);
        buffer.subscribe0(reader, upstream);
        List<Throwable> dropped = new ArrayList<>();
        Hooks.onErrorDropped(dropped::add);
        try {
            reader.asFlux(upstream).subscribe(ReadBuffer::close).dispose();
            assertEquals(List.of("discard", "backpressure"), calls);
            assertEquals(List.of(failure), dropped);
        } finally {
            Hooks.resetOnErrorDropped();
            buffer.complete();
        }
    }

    @Test
    void cancelledReaderDoesNotChargeTheSharedBufferBudget() {
        BufferConsumer.Upstream upstream = bytesConsumed -> { };
        BaseSharedBuffer buffer = new BaseSharedBuffer(ReadBufferFactory.getJdkFactory(), BodySizeLimits.UNLIMITED, upstream) { };
        SizeLimitTracker.TrackerPair tracker = SizeLimitTracker.notThreadSafe(new BodySizeLimits(Long.MAX_VALUE, 100)).makeBothAtomic();
        buffer.addSizeLimitTrackers(tracker);
        BaseSharedBuffer.AsFlux reader = new BaseSharedBuffer.AsFlux(buffer);
        buffer.subscribe0(reader, upstream);
        synchronized (buffer) {
            reader.asFlux(upstream).subscribe(ReadBuffer::close).dispose();
        }

        buffer.add(ReadBufferFactory.getJdkFactory().adapt(new byte[60]));

        assertNull(tracker.bufferedSize().add(100));
    }
}
