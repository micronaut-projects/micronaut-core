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
import io.micronaut.http.body.CloseableByteBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;

import java.io.InputStream;
import java.util.Arrays;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cancelling an InputStream body (a client disconnect, on the event loop) must not run the
 * possibly blocking {@link InputStream#close()} on the cancelling thread.
 */
class InputStreamCancelThreadTest {

    @Test
    @Timeout(10)
    void closeDoesNotRunOnTheCancellingThread() throws Exception {
        AtomicReference<Thread> closedOn = new AtomicReference<>();
        CountDownLatch closed = new CountDownLatch(1);
        CountDownLatch firstItem = new CountDownLatch(1);
        InputStream endless = new InputStream() {
            @Override
            public int read() {
                return 'a';
            }

            @Override
            public int read(byte[] b, int off, int len) {
                Arrays.fill(b, off, off + len, (byte) 'a');
                return len;
            }

            @Override
            public void close() {
                closedOn.compareAndSet(null, Thread.currentThread());
                closed.countDown();
            }
        };
        ExecutorService io = Executors.newSingleThreadExecutor();
        try {
            CloseableByteBody body = InputStreamByteBody.create(endless, OptionalLong.empty(), io, ByteArrayBufferFactory.INSTANCE);
            BaseSubscriber<byte[]> subscriber = new BaseSubscriber<>() {
                @Override
                protected void hookOnSubscribe(Subscription subscription) {
                    request(1);
                }

                @Override
                protected void hookOnNext(byte[] value) {
                    firstItem.countDown();
                }
            };
            Flux.from(body.toByteArrayPublisher()).subscribe(subscriber);
            assertTrue(firstItem.await(5, TimeUnit.SECONDS));

            Thread canceller = Thread.currentThread();
            subscriber.cancel();

            assertTrue(closed.await(5, TimeUnit.SECONDS));
            assertNotSame(canceller, closedOn.get(), "InputStream.close() ran on the thread that cancelled the body (the event loop)");
        } finally {
            io.shutdownNow();
        }
    }
}
