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
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(30)
class ConcurrentByteBodyClaimTest {
    @Test
    void closeRacingWithAStreamingClaimConsumesTheReservationOnce() throws Exception {
        ByteBodyFactory factory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        List<Consumer<CloseableByteBody>> claims = List.of(
            body -> body.move().close(),
            body -> body.buffer().join().close(),
            body -> Flux.from(body.toReadBufferPublisher()).subscribe(ReadBuffer::close)
        );
        try (var executor = Executors.newFixedThreadPool(2)) {
            CyclicBarrier start = new CyclicBarrier(3);
            for (Consumer<CloseableByteBody> claim : claims) {
                for (int i = 0; i < 300; i++) {
                    AtomicInteger starts = new AtomicInteger();
                    var streaming = factory.createStreamingBody(BodySizeLimits.UNLIMITED, new BufferConsumer.Upstream() {
                        @Override
                        public void start() {
                            starts.incrementAndGet();
                        }

                        @Override
                        public void onBytesConsumed(long bytes) {
                        }
                    });
                    streaming.sharedBuffer().addAndComplete(factory.readBufferFactory().adapt(new byte[1]));
                    CloseableByteBody body = streaming.rootBody();
                    var claiming = executor.submit(() -> {
                        start.await();
                        try {
                            claim.accept(body);
                        } catch (IllegalStateException e) {
                            // A claim that loses to close must fail before consuming a reservation.
                            assertEquals(IllegalStateException.class, e.getClass());
                        }
                        return null;
                    });
                    var closing = executor.submit(() -> {
                        start.await();
                        body.close();
                        return null;
                    });
                    start.await();
                    claiming.get();
                    closing.get();
                    body.close();
                    assertEquals(1, starts.get());
                }
            }
        }
    }
}
