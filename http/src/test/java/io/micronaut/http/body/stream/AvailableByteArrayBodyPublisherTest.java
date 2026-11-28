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
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The publisher of an {@link AvailableByteArrayBody} owns the buffer until it is delivered.
 */
class AvailableByteArrayBodyPublisherTest {

    @Test
    void cancellingBeforeRequestingReleasesTheBuffer() {
        CountingReadBuffer buffer = new CountingReadBuffer("foo");
        Recorder recorder = new Recorder();
        AvailableByteArrayBody.create(buffer).toReadBufferPublisher().subscribe(recorder);

        recorder.subscription.get().cancel();

        assertEquals(List.of(), recorder.items);
        assertEquals(1, buffer.closes.get(), "the undelivered buffer was not released");
    }

    @Test
    void cancellingAfterTheDeliveryDoesNotReleaseTheBufferAgain() {
        CountingReadBuffer buffer = new CountingReadBuffer("foo");
        Recorder recorder = new Recorder();
        AvailableByteArrayBody.create(buffer).toReadBufferPublisher().subscribe(recorder);

        recorder.subscription.get().request(1);
        recorder.subscription.get().cancel();

        assertEquals(1, recorder.items.size());
        assertSame(buffer, recorder.items.get(0));
        assertEquals(1, recorder.completions);
        // the subscriber owns the delivered buffer
        assertEquals(0, buffer.closes.get());
        recorder.items.get(0).close();
        assertEquals(1, buffer.closes.get());
    }

    private static final class Recorder implements Subscriber<ReadBuffer> {
        final AtomicReference<Subscription> subscription = new AtomicReference<>();
        final List<ReadBuffer> items = new ArrayList<>();
        int completions;

        @Override
        public void onSubscribe(Subscription s) {
            subscription.set(s);
        }

        @Override
        public void onNext(ReadBuffer readBuffer) {
            items.add(readBuffer);
        }

        @Override
        public void onError(Throwable t) {
            throw new AssertionError(t);
        }

        @Override
        public void onComplete() {
            completions++;
        }
    }
}
