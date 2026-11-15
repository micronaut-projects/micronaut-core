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
package io.micronaut.http.netty.body;

import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(30)
class ConcurrentNettyBodyClaimTest {
    @Test
    void closeRacingWithStreamingAndAvailableClaimsReleasesOnce() throws Exception {
        List<Consumer<CloseableByteBody>> claims = List.of(
            body -> body.move().close(),
            body -> body.buffer().join().close(),
            body -> Flux.from(body.toReadBufferPublisher()).subscribe(ReadBuffer::close)
        );
        EmbeddedChannel channel = new EmbeddedChannel();
        NettyByteBodyFactory factory = new NettyByteBodyFactory(channel);
        try (var executor = Executors.newFixedThreadPool(2)) {
            CyclicBarrier start = new CyclicBarrier(3);
            for (boolean streaming : new boolean[]{false, true}) {
                for (Consumer<CloseableByteBody> claim : claims) {
                    for (int i = 0; i < 300; i++) {
                        var nativeBuffer = Unpooled.wrappedBuffer(new byte[]{1});
                        CloseableByteBody body;
                        if (streaming) {
                            BufferConsumer.Upstream upstream = bytes -> { };
                            var shared = factory.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
                            shared.sharedBuffer().addAndComplete(factory.readBufferFactory().adapt(nativeBuffer));
                            body = shared.rootBody();
                        } else {
                            body = factory.adapt(nativeBuffer);
                        }
                        var claiming = executor.submit(() -> {
                            start.await();
                            try {
                                claim.accept(body);
                            } catch (IllegalStateException e) {
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
                        channel.runPendingTasks();
                        body.close();
                        channel.runPendingTasks();
                        assertEquals(0, nativeBuffer.refCnt());
                    }
                }
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
