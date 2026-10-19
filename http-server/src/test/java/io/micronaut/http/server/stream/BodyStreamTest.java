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
package io.micronaut.http.server.stream;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.stream.BaseStreamingByteBody;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.exceptions.ConnectionClosedException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The backpressure accounting of {@link BodyStream}: the stage of a write completes once the
 * bytes up to it, not taken by the connection, are below the high-water mark; the queue is bounded
 * at sixteen times the mark; the end of the stream settles the pending stages.
 */
class BodyStreamTest {

    private static final ByteBodyFactory FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    @Test
    void writesCompleteBelowTheHighWaterMark() {
        Consumer consumer = new Consumer();
        BodyStream stream = open(10, consumer);
        CompletableFuture<Void> first = write(stream, "12345");
        assertTrue(first.isDone(), "below the mark");
        assertTrue(stream.isWritable());
        CompletableFuture<Void> second = write(stream, "67890");
        assertFalse(second.isDone(), "10 bytes queued, not below a mark of 10");
        assertFalse(stream.isWritable());
        CompletableFuture<Void> third = write(stream, "abcdefgh");
        consumer.upstream().onBytesConsumed(5);
        assertTrue(second.isDone(), "5 bytes queued up to the second write");
        assertFalse(third.isDone(), "13 bytes queued up to the third write");
        consumer.upstream().onBytesConsumed(4);
        assertTrue(third.isDone(), "9 bytes queued up to the third write");
        assertEquals("1234567890abcdefgh", consumer.received());
    }

    @Test
    void theQueueIsBoundedAtSixteenTimesTheMark() {
        Consumer consumer = new Consumer();
        BodyStream stream = open(4, consumer);
        AtomicReference<@Nullable Throwable> closed = new AtomicReference<>();
        stream.onClose(closed::set);
        for (int i = 0; i < BodyStream.BUFFER_LIMIT_FACTOR; i++) {
            write(stream, "1234");
        }
        CompletableFuture<Void> overflow = write(stream, "x");
        assertTrue(overflow.isCompletedExceptionally());
        assertInstanceOf(ConnectionClosedException.class, closed.get());
        assertFalse(stream.isOpen());
        assertInstanceOf(ConnectionClosedException.class, consumer.error);
    }

    @Test
    void completionSettlesThePendingWrites() {
        Consumer consumer = new Consumer();
        BodyStream stream = open(1, consumer);
        CompletableFuture<Void> pending = write(stream, "abc");
        AtomicReference<String> closed = new AtomicReference<>();
        stream.onClose(cause -> closed.set(String.valueOf(cause)));
        assertTrue(stream.complete());
        assertTrue(pending.isDone() && !pending.isCompletedExceptionally());
        assertEquals("null", closed.get());
        assertTrue(consumer.complete);
        assertTrue(write(stream, "late").isCompletedExceptionally());
        assertFalse(stream.complete(), "already closed");
        // a callback added after the close runs at once
        AtomicReference<String> late = new AtomicReference<>();
        stream.onClose(cause -> late.set(String.valueOf(cause)));
        assertEquals("null", late.get());
    }

    @Test
    void discardFailsThePendingWrites() {
        Consumer consumer = new Consumer();
        BodyStream stream = open(1, consumer);
        CompletableFuture<Void> pending = write(stream, "abc");
        AtomicReference<@Nullable Throwable> closed = new AtomicReference<>();
        stream.onClose(closed::set);
        consumer.upstream().allowDiscard();
        assertTrue(pending.isCompletedExceptionally());
        assertInstanceOf(ConnectionClosedException.class, closed.get());
        assertTrue(write(stream, "late").isCompletedExceptionally());
    }

    @Test
    void discardOfABodylessResponseIsANormalEnd() {
        BodyStream stream = new BodyStream(FACTORY, PropagatedContext.empty(), 10, true);
        AtomicReference<String> closed = new AtomicReference<>();
        stream.onClose(cause -> closed.set(String.valueOf(cause)));
        // the server closes the body of a HEAD response
        stream.body().close();
        assertEquals("null", closed.get());
    }

    @Test
    void failureFailsThePendingWrites() {
        Consumer consumer = new Consumer();
        BodyStream stream = open(1, consumer);
        CompletableFuture<Void> pending = write(stream, "abc");
        IllegalStateException failure = new IllegalStateException("broken");
        AtomicReference<@Nullable Throwable> closed = new AtomicReference<>();
        stream.onClose(closed::set);
        assertTrue(stream.fail(failure));
        assertSame(failure, closed.get());
        assertTrue(pending.isCompletedExceptionally());
        assertSame(failure, consumer.error);
    }

    @Test
    void demandIsSignalledWhenTheStreamBecomesWritable() {
        Consumer consumer = new Consumer();
        BodyStream stream = open(4, consumer);
        List<String> signals = new ArrayList<>();
        stream.onDemand(() -> signals.add("demand"));
        write(stream, "12345");
        consumer.upstream().onBytesConsumed(1);
        assertEquals(List.of(), signals, "4 bytes queued");
        consumer.upstream().onBytesConsumed(1);
        assertEquals(List.of("demand"), signals);
    }

    private static BodyStream open(int highWaterMark, Consumer consumer) {
        BodyStream stream = new BodyStream(FACTORY, PropagatedContext.empty(), highWaterMark, false);
        consumer.upstream = ((BaseStreamingByteBody<?>) stream.body()).primary(consumer);
        return stream;
    }

    private static CompletableFuture<Void> write(BodyStream stream, String data) {
        CompletionStage<Void> stage = stream.write(FACTORY.readBufferFactory().copyOf(data, StandardCharsets.UTF_8));
        return stage.toCompletableFuture();
    }

    /**
     * The connection: takes what it is given, and reports what it took only when told.
     */
    private static final class Consumer implements BufferConsumer {
        private final ByteArrayOutputStream received = new ByteArrayOutputStream();
        private BufferConsumer.@Nullable Upstream upstream;
        private boolean complete;
        private @Nullable Throwable error;

        BufferConsumer.Upstream upstream() {
            BufferConsumer.Upstream u = upstream;
            if (u == null) {
                throw new IllegalStateException("not subscribed");
            }
            return u;
        }

        String received() {
            return received.toString(StandardCharsets.UTF_8);
        }

        @Override
        public void add(ReadBuffer rb) {
            try (rb) {
                received.writeBytes(rb.toArray());
            }
        }

        @Override
        public void complete() {
            complete = true;
        }

        @Override
        public void error(Throwable e) {
            error = e;
        }
    }
}
