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

import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.exceptions.BufferLengthExceededException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
class SharedBufferTerminalStateTest {
    private static final ReadBufferFactory FACTORY = ReadBufferFactory.getJdkFactory();
    private static final BufferConsumer.Upstream UPSTREAM = bytes -> { };

    private BaseSharedBuffer buffer(BodySizeLimits limits) {
        return new BaseSharedBuffer(FACTORY, limits, UPSTREAM) {
            @Override
            protected void submitDeferred(Runnable task) {
                task.run();
            }
        };
    }

    @Test
    void repeatedCompletionAndFailureAreIdempotent() {
        for (boolean fail : List.of(false, true)) {
            BaseSharedBuffer buffer = buffer(BodySizeLimits.UNLIMITED);
            Reader reader = new Reader();
            Reader late = new Reader();
            buffer.reserve0();
            buffer.subscribe0(reader, UPSTREAM);
            IOException failure = new IOException("first");
            if (fail) {
                buffer.error(failure);
            } else {
                buffer.complete();
            }
            buffer.complete();
            if (fail) {
                buffer.error(new IOException("later"));
            }
            buffer.complete();
            buffer.subscribe0(late, UPSTREAM);
            for (Reader r : List.of(reader, late)) {
                assertEquals(fail ? 0 : 1, r.completions);
                assertEquals(fail ? List.of(failure) : List.of(), r.errors);
            }
        }
    }

    @Test
    void failureAfterCompletionInvalidatesUnclaimedBytesWithoutSignallingCompletedReaders() {
        BaseSharedBuffer buffer = buffer(BodySizeLimits.UNLIMITED);
        Reader reader = new Reader();
        Reader late = new Reader();
        buffer.reserve0();
        buffer.subscribe0(reader, UPSTREAM);
        buffer.add(FACTORY.adapt(new byte[1]));
        buffer.complete();
        IOException failure = new IOException("reset");
        buffer.error(failure);
        buffer.error(new IOException("later"));
        buffer.complete();
        buffer.subscribe0(late, UPSTREAM);
        assertEquals(1, reader.completions);
        assertTrue(reader.errors.isEmpty());
        assertEquals(0, late.bytes);
        assertEquals(List.of(failure), late.errors);
    }

    @Test
    void throwingCompletionIsNotRetriedAndStillCompletesFullReaders() {
        BaseSharedBuffer buffer = buffer(BodySizeLimits.UNLIMITED);
        AtomicInteger calls = new AtomicInteger();
        buffer.reserve0();
        buffer.subscribe0(new Reader() {
            @Override
            public void complete() {
                calls.incrementAndGet();
                throw new IllegalStateException("callback");
            }
        }, UPSTREAM);
        DelayedExecutionFlow<ReadBuffer> full = DelayedExecutionFlow.create();
        buffer.subscribeFull0(full, UPSTREAM, false);
        buffer.add(FACTORY.adapt(new byte[3]));

        assertThrows(IllegalStateException.class, buffer::complete);
        buffer.complete();
        assertEquals(1, calls.get());
        try (ReadBuffer result = full.toCompletableFuture().join()) {
            assertEquals(3, result.readable());
        }
    }

    @Test
    void streamingCompletionCanCancelAFullReaderWithoutLeakingItsBytes() {
        BaseSharedBuffer buffer = buffer(BodySizeLimits.UNLIMITED);
        AtomicInteger copies = new AtomicInteger();
        DelayedExecutionFlow<ReadBuffer> full = DelayedExecutionFlow.create();
        full.onCancel(() -> buffer.cancelFull0(full, UPSTREAM));
        buffer.reserve0();
        buffer.subscribe0(new Reader() {
            @Override
            public void complete() {
                full.cancel();
            }
        }, UPSTREAM);
        buffer.subscribeFull0(full, UPSTREAM, false);
        buffer.add(new CountingBuffer(copies));
        buffer.complete();
        buffer.complete();
        assertTrue(full.isCancelled());
        assertEquals(0, copies.get());
    }

    @Test
    void finalCopiesAreClosedWhenFullCompletionThrows() {
        BaseSharedBuffer buffer = buffer(BodySizeLimits.UNLIMITED);
        AtomicInteger copies = new AtomicInteger();
        buffer.reserve0();
        buffer.subscribe0(new Reader(), UPSTREAM);
        DelayedExecutionFlow<ReadBuffer> full = DelayedExecutionFlow.create();
        full.onComplete((data, error) -> {
            data.close();
            throw new AssertionError("completion");
        });
        buffer.subscribeFull0(full, UPSTREAM, false);

        assertThrows(AssertionError.class, () -> buffer.addAndComplete(new CountingBuffer(copies)));
        assertEquals(0, copies.get());
        buffer.complete();
    }


    @Test
    void completionDuringInitialDeliveryIsNotRepeatedBySubscribe() {
        BaseSharedBuffer buffer = buffer(BodySizeLimits.UNLIMITED);
        buffer.add(FACTORY.adapt(new byte[1]));
        Reader reader = new Reader() {
            @Override
            public void add(ReadBuffer rb) {
                rb.close();
                buffer.complete();
            }
        };
        buffer.subscribe0(reader, UPSTREAM);
        assertEquals(1, reader.completions);
    }

    @Test
    void overflowStillAllowsExistingStreamsToFinishAndRejectsLateReaders() {
        BaseSharedBuffer buffer = buffer(new BodySizeLimits(Long.MAX_VALUE, 1));
        Reader reader = new Reader();
        Reader late = new Reader();
        buffer.reserve0();
        buffer.subscribe0(reader, UPSTREAM);
        buffer.add(FACTORY.adapt(new byte[2]));
        buffer.add(FACTORY.adapt(new byte[2]));
        buffer.complete();
        buffer.complete();
        buffer.subscribe0(late, UPSTREAM);
        assertEquals(4, reader.bytes);
        assertEquals(1, reader.completions);
        assertTrue(reader.errors.isEmpty());
        assertEquals(1, late.errors.size());
        assertInstanceOf(BufferLengthExceededException.class, late.errors.getFirst());
        assertEquals(0, late.completions);
    }

    @Test
    void contentLengthFailuresStillThrowAtTheCaller() {
        BaseSharedBuffer buffer = buffer(BodySizeLimits.UNLIMITED);
        buffer.setExpectedLength(2);
        assertThrows(BaseSharedBuffer.IncorrectContentLengthException.class, buffer::complete);
        assertThrows(BaseSharedBuffer.IncorrectContentLengthException.class, () -> buffer.add(FACTORY.adapt(new byte[3])));
        buffer.add(FACTORY.adapt(new byte[2]));
        buffer.complete();
        buffer.subscribe0(null, UPSTREAM);
    }

    @Test
    void completionAfterFailureStillFixesTheExpectedLengthAtTheCaller() {
        BaseSharedBuffer buffer = buffer(BodySizeLimits.UNLIMITED);
        buffer.error(new IOException("test"));
        buffer.complete();
        assertEquals(0, buffer.getExpectedLength().orElseThrow());
        assertThrows(BaseSharedBuffer.IncorrectContentLengthException.class, () -> buffer.add(FACTORY.adapt(new byte[1])));
        buffer.subscribe0(null, UPSTREAM);
    }

    private static class Reader implements BufferConsumer {
        int bytes;
        int completions;
        final List<Throwable> errors = new ArrayList<>();

        @Override
        public void add(ReadBuffer rb) {
            bytes += rb.readable();
            rb.close();
        }

        @Override
        public void complete() {
            completions++;
        }

        @Override
        public void error(Throwable e) {
            errors.add(e);
        }
    }

    private static class CountingBuffer extends ReadBuffer {
        private final AtomicInteger copies;
        private boolean consumed;

        CountingBuffer(AtomicInteger copies) {
            this.copies = copies;
            copies.incrementAndGet();
        }

        @Override
        public int readable() {
            return 1;
        }

        @Override
        public ReadBuffer duplicate() {
            return new CountingBuffer(copies);
        }

        @Override
        public ReadBuffer split(int position) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ReadBuffer move() {
            ReadBuffer moved = duplicate();
            close();
            return moved;
        }

        @Override
        public void toArray(byte[] destination, int offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            if (!consumed) {
                consumed = true;
                copies.decrementAndGet();
            }
        }

        @Override
        protected boolean isConsumed() {
            return consumed;
        }

        @Override
        protected byte[] peekArray(int n) {
            throw new UnsupportedOperationException();
        }
    }
}
