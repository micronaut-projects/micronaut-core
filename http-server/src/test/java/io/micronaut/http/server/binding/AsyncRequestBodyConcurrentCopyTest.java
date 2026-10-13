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
package io.micronaut.http.server.binding;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.simple.SimpleHttpRequest;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * A copy of the body read together with the body itself, on a request of unknown length whose
 * bytes arrive in chunks on another thread: both read all the bytes, in order.
 */
class AsyncRequestBodyConcurrentCopyTest {
    private static final ByteBodyFactory FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    @Test
    void aCopyReadTogetherWithTheBodyLeavesTheBodyIntact() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            AsyncRequestBodyArgumentBinder binder = ctx.getBean(AsyncRequestBodyArgumentBinder.class);
            Random random = new Random(42);
            for (int size : new int[] {5000, 300_000, 1_500_000}) {
                for (int chunk : new int[] {1000, 8192}) {
                    for (int i = 0; i < 25; i++) {
                        byte[] data = new byte[size];
                        random.nextBytes(data);
                        Chunks chunks = new Chunks(data, chunk);
                        Server server = new Server(FACTORY.adapt(chunks, new BodySizeLimits(Long.MAX_VALUE, 10 * 1024 * 1024), null, null));
                        DefaultAsyncRequestBody body = new DefaultAsyncRequestBody(server, server, binder);
                        AsyncRequestBody copy = body.copy();
                        CompletableFuture<byte[]> copied = copy.bytes(Integer.MAX_VALUE - 8).toCompletableFuture();
                        CompletableFuture<byte[]> original = body.bytes(Integer.MAX_VALUE - 8).toCompletableFuture();
                        CompletableFuture.runAsync(chunks::arrive);
                        assertArrayEquals(data, copied.get(10, TimeUnit.SECONDS), "copy, size " + size + " chunk " + chunk);
                        assertArrayEquals(data, original.get(10, TimeUnit.SECONDS), "original, size " + size + " chunk " + chunk);
                        body.releaseBody().toCompletableFuture().get(10, TimeUnit.SECONDS);
                        server.bytes.close();
                    }
                }
            }
        }
    }

    /**
     * The chunks of the data, like a servlet input stream read asynchronously: the first chunk
     * arrives on another thread, once the test lets it, and each chunk asked for after that is
     * emitted on the thread that asks for it, as long as the bytes are there, before the request
     * returns. A request made while a chunk is delivered, e.g. by a reader that consumed it,
     * delivers the next chunk before that delivery returned.
     */
    private static final class Chunks implements Publisher<ReadBuffer>, Subscription {
        private final byte[] data;
        private final int chunk;
        private Subscriber<? super ReadBuffer> subscriber;
        private long demand;
        private int offset;
        private boolean arrived;
        private boolean done;

        Chunks(byte[] data, int chunk) {
            this.data = data;
            this.chunk = chunk;
        }

        @Override
        public void subscribe(Subscriber<? super ReadBuffer> s) {
            subscriber = s;
            s.onSubscribe(this);
        }

        void arrive() {
            synchronized (this) {
                arrived = true;
            }
            emit();
        }

        @Override
        public void request(long n) {
            synchronized (this) {
                demand += n;
            }
            emit();
        }

        private void emit() {
            ReadBuffer next;
            synchronized (this) {
                if (!arrived || done || demand == 0) {
                    return;
                }
                if (offset >= data.length) {
                    done = true;
                    next = null;
                } else {
                    demand--;
                    int end = Math.min(data.length, offset + chunk);
                    next = FACTORY.readBufferFactory().adapt(Arrays.copyOfRange(data, offset, end));
                    offset = end;
                }
            }
            if (next == null) {
                subscriber.onComplete();
            } else {
                subscriber.onNext(next);
                if (offset >= data.length) {
                    emit();
                }
            }
        }

        @Override
        public synchronized void cancel() {
            done = true;
        }
    }

    private static final class Server extends HttpRequestWrapper<Object> implements ServerHttpRequest<Object> {
        final CloseableByteBody bytes;

        Server(CloseableByteBody bytes) {
            super(new SimpleHttpRequest<>(HttpMethod.POST, "/", null));
            this.bytes = bytes;
        }

        @Override
        public ByteBody byteBody() {
            return bytes;
        }

        @Override
        public ByteBodyFactory byteBodyFactory() {
            return FACTORY;
        }
    }
}
