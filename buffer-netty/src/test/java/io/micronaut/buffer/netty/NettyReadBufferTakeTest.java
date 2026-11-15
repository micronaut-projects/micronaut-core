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
package io.micronaut.buffer.netty;

import io.micronaut.core.io.buffer.ReadBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(30)
class NettyReadBufferTakeTest {
    @Test
    void closeRacingWithEachConsumingOperationReleasesOnce() throws Exception {
        List<Consumer<ReadBuffer>> claims = List.of(
            buffer -> buffer.move().close(),
            buffer -> buffer.toArray(new byte[1], 0),
            buffer -> buffer.toString(StandardCharsets.UTF_8),
            buffer -> ((NettyByteBuffer) buffer.toByteBuffer()).release(),
            buffer -> NettyReadBufferFactory.toByteBuf(buffer).release(),
            buffer -> buffer.useFastHeapBuffer(bb -> bb.remaining()),
            buffer -> {
                try (var stream = buffer.toInputStream()) {
                    stream.readAllBytes();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            },
            buffer -> {
                try {
                    buffer.transferTo(new ByteArrayOutputStream());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            },
            ReadBuffer::close
        );
        try (var executor = Executors.newFixedThreadPool(2)) {
            CyclicBarrier start = new CyclicBarrier(3);
            for (Consumer<ReadBuffer> claim : claims) {
                for (int i = 0; i < 300; i++) {
                    ByteBuf nativeBuffer = Unpooled.wrappedBuffer(new byte[]{1});
                    ReadBuffer buffer = NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT).adapt(nativeBuffer);
                    var claiming = executor.submit(() -> {
                        start.await();
                        try {
                            claim.accept(buffer);
                        } catch (IllegalStateException e) {
                            assertEquals(IllegalStateException.class, e.getClass());
                            assertEquals("Already released", e.getMessage());
                        }
                        return null;
                    });
                    var closing = executor.submit(() -> {
                        start.await();
                        buffer.close();
                        return null;
                    });
                    start.await();
                    claiming.get();
                    closing.get();
                    buffer.close();
                    assertEquals(0, nativeBuffer.refCnt());
                }
            }
        }
    }

    @Test
    void failingConsumingOperationAlsoTakesOwnershipOnce() {
        for (boolean callback : new boolean[]{false, true}) {
            ByteBuf nativeBuffer = Unpooled.wrappedBuffer(new byte[]{1});
            ReadBuffer buffer = NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT).adapt(nativeBuffer);
            if (callback) {
                assertThrows(IllegalArgumentException.class, () -> buffer.useFastHeapBuffer(bb -> {
                    throw new IllegalArgumentException("callback");
                }));
            } else {
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.toArray(new byte[0], 1));
            }
            buffer.close();
            assertEquals(0, nativeBuffer.refCnt());
        }
    }
}
