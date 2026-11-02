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
import io.micronaut.http.body.stream.InputStreamByteBody;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A streaming piece that arrives after allowDiscard (reactive streams 2.8 allows onNext after
 * cancel) is attached and started, and never discarded.
 */
class ConcatenatingSubscriberLateItemTest {

    @Test
    void pieceArrivingAfterAllowDiscardIsDiscarded() throws Exception {
        ByteBodyFactory bbf = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        ExecutorService io = Executors.newSingleThreadExecutor();
        try {
            AtomicBoolean closed = new AtomicBoolean();
            AtomicInteger reads = new AtomicInteger();
            FilterInputStream stream = new FilterInputStream(new ByteArrayInputStream(new byte[1024 * 1024])) {
                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    reads.incrementAndGet();
                    return super.read(b, off, Math.min(len, 1024));
                }

                @Override
                public void close() throws IOException {
                    closed.set(true);
                    super.close();
                }
            };

            ConcatenatingSubscriber subscriber = new ConcatenatingSubscriber(bbf, ConcatenatingSubscriber.Separators.NONE);
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    // not needed for this test
                }

                @Override
                public void cancel() {
                    // not needed for this test
                }
            });
            subscriber.start();
            // the response is abandoned (client disconnected)
            subscriber.allowDiscard();
            // a piece already in flight arrives after the cancel
            subscriber.onNext(InputStreamByteBody.create(stream, OptionalLong.empty(), io, bbf));

            io.submit(() -> { }).get(5, TimeUnit.SECONDS);
            Thread.sleep(200);
            io.submit(() -> { }).get(5, TimeUnit.SECONDS);
            assertTrue(closed.get(), "the late piece's source stream was left open after " + reads.get() + " reads");
            assertEquals(0, reads.get());
        } finally {
            io.shutdownNow();
        }
    }
}
