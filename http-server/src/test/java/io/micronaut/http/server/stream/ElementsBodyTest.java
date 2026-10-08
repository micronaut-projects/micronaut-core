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

import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BaseStreamingByteBody;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.exceptions.ConnectionClosedException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pull loop of a {@link BodyElements} response body: elements are pulled one at a time while
 * the stream is writable, a failure of the first element fails the body before anything is sent,
 * a later one fails the stream, and a streamed element is forwarded as its bytes arrive.
 */
class ElementsBodyTest {

    private static final ByteBodyFactory FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    @Test
    void theElementsAreWrittenInOrderThenTheEnd() throws Exception {
        Elements elements = new Elements(List.of("1", "2", "3"));
        Encoder encoder = new Encoder();
        Consumer consumer = read(ResponseStreams.stream(FACTORY, elements, encoder, 1024));
        assertEquals("1,2,3,end", consumer.received());
        assertTrue(consumer.complete);
        assertTrue(encoder.closed.get());
        assertTrue(elements.closed.get());
    }

    @Test
    void noElementIsTheEndOnly() throws Exception {
        Consumer consumer = read(ResponseStreams.stream(FACTORY, new Elements(List.of()), new Encoder(), 1024));
        assertEquals("none", consumer.received());
        assertTrue(consumer.complete);
    }

    @Test
    void anEndWithoutBytesCompletesTheBody() throws Exception {
        Encoder encoder = new Encoder();
        encoder.end = none -> null;
        Consumer consumer = read(ResponseStreams.stream(FACTORY, new Elements(List.of("1")), encoder, 1024));
        assertEquals("1,", consumer.received());
        assertTrue(consumer.complete);
    }

    @Test
    void aFailureOfTheFirstElementFailsTheBodyBeforeAnythingIsSent() {
        IllegalStateException failure = new IllegalStateException("no element");
        // next() throws, returns no stage, or a failed stage
        assertSame(failure, firstFailure(BodyElements.of(() -> {
            throw failure;
        }), new Encoder()));
        Throwable noStage = firstFailure(BodyElements.of(() -> null), new Encoder());
        assertInstanceOf(NullPointerException.class, noStage);
        assertSame(failure, firstFailure(BodyElements.of(() -> CompletableFuture.failedFuture(new CompletionException(failure))), new Encoder()));
        // the encoder throws, fails, or encodes to nothing
        Encoder throwing = new Encoder();
        throwing.encode = element -> {
            throw failure;
        };
        assertSame(failure, firstFailure(new Elements(List.of("1")), throwing));
        Encoder failing = new Encoder();
        failing.encode = element -> ExecutionFlow.error(failure);
        assertSame(failure, firstFailure(new Elements(List.of("1")), failing));
        Encoder empty = new Encoder();
        empty.encode = element -> ExecutionFlow.empty();
        assertInstanceOf(NullPointerException.class, firstFailure(new Elements(List.of("1")), empty));
        // the elements and the encoder are closed
        Elements elements = new Elements(List.of("1"));
        Encoder closed = new Encoder();
        closed.encode = element -> ExecutionFlow.error(failure);
        firstFailure(elements, closed);
        assertTrue(elements.closed.get());
        assertTrue(closed.closed.get());
    }

    @Test
    void aLaterFailureFailsTheStream() throws Exception {
        IllegalStateException failure = new IllegalStateException("second");
        CompletableFuture<Optional<String>> second = new CompletableFuture<>();
        AtomicInteger count = new AtomicInteger();
        BodyElements<String> elements = BodyElements.of(() -> count.getAndIncrement() == 0
            ? CompletableFuture.completedFuture(Optional.of("1"))
            : second);
        Consumer consumer = read(ResponseStreams.stream(FACTORY, elements, new Encoder(), 1024));
        second.completeExceptionally(failure);
        assertEquals("1,", consumer.received());
        assertSame(failure, consumer.error);
    }

    @Test
    void elementsArePulledWhileTheStreamIsWritable() throws Exception {
        AtomicInteger pulls = new AtomicInteger();
        AtomicBoolean closed = new AtomicBoolean();
        BodyElements<String> elements = BodyElements.of(
            () -> CompletableFuture.completedFuture(Optional.of(String.valueOf(pulls.getAndIncrement() % 10))),
            () -> closed.set(true));
        Consumer consumer = new Consumer(false);
        consumer.subscribe(ResponseStreams.stream(FACTORY, elements, new Encoder(), 4));
        // "0," and "1,": four bytes are not below the mark of four
        assertEquals(2, pulls.get());
        consumer.upstream().onBytesConsumed(4);
        assertEquals(4, pulls.get());
        assertEquals("0,1,2,3,", consumer.received());
        // the client leaves: the elements are closed, and no longer pulled
        consumer.upstream().allowDiscard();
        assertTrue(closed.get());
        assertEquals(4, pulls.get());
    }

    @Test
    void asynchronousElementsArePulledOnTheCompletingThread() throws Exception {
        List<CompletableFuture<Optional<String>>> reads = new ArrayList<>();
        BodyElements<String> elements = BodyElements.of(() -> {
            CompletableFuture<Optional<String>> read = new CompletableFuture<>();
            synchronized (reads) {
                reads.add(read);
            }
            return read;
        });
        ExecutionFlow<CloseableByteBody> flow = ResponseStreams.stream(FACTORY, elements, new Encoder(), 1024);
        assertNull(flow.tryComplete(), "no element yet");
        Thread thread = new Thread(() -> {
            read(reads, 0).complete(Optional.of("a"));
            read(reads, 1).complete(Optional.of("b"));
            read(reads, 2).complete(Optional.empty());
        });
        thread.start();
        thread.join(10_000);
        Consumer consumer = read(flow);
        assertEquals("a,b,end", consumer.received());
        assertTrue(consumer.complete);
    }

    @Test
    void anElementThatArrivesAfterTheClientLeftIsDropped() throws Exception {
        CompletableFuture<Optional<String>> second = new CompletableFuture<>();
        AtomicInteger pulls = new AtomicInteger();
        // not closed by the close: elements that do not follow the rules
        BodyElements<String> elements = () -> pulls.getAndIncrement() == 0
            ? CompletableFuture.completedFuture(Optional.of("1"))
            : second;
        Encoder encoder = new Encoder();
        Consumer consumer = new Consumer(true);
        consumer.subscribe(ResponseStreams.stream(FACTORY, elements, encoder, 1024));
        consumer.upstream().allowDiscard();
        assertTrue(encoder.closed.get());
        // a read that completes after the close is not written, and the pull stops
        second.complete(Optional.of("2"));
        assertEquals("1,", consumer.received());
        assertEquals(2, pulls.get());
    }

    @Test
    void aStreamedElementIsForwardedAsItsBytesArrive() throws Exception {
        BodyStream piece = new BodyStream(FACTORY, PropagatedContext.empty(), 1024, false);
        // bytes the element had before it was forwarded
        piece.write(copyOf("ab"));
        Encoder encoder = new Encoder();
        encoder.encode = element -> element.equals("streamed")
            ? ExecutionFlow.just(piece.body())
            : ExecutionFlow.just(FACTORY.copyOf(element + ",", StandardCharsets.UTF_8));
        Consumer consumer = new Consumer(true);
        consumer.subscribe(ResponseStreams.stream(FACTORY, new Elements(List.of("streamed", "after")), encoder, 4));
        assertEquals("ab", consumer.received());
        piece.write(copyOf("cdefgh"));
        assertEquals("abcdefgh", consumer.received());
        assertFalse(consumer.complete);
        piece.complete();
        assertEquals("abcdefghafter,end", consumer.received());
        assertTrue(consumer.complete);
    }

    @Test
    void aStreamedElementIsPausedBySlowClient() throws Exception {
        BodyStream piece = new BodyStream(FACTORY, PropagatedContext.empty(), 2, false);
        Encoder encoder = new Encoder();
        encoder.encode = element -> ExecutionFlow.just(piece.body());
        Consumer consumer = new Consumer(false);
        ExecutionFlow<CloseableByteBody> flow = ResponseStreams.stream(FACTORY, new Elements(List.of("streamed")), encoder, 2);
        CompletableFuture<Void> first = piece.write(copyOf("ab")).toCompletableFuture();
        consumer.subscribe(flow);
        assertEquals("ab", consumer.received());
        // the element got a mark of bytes in advance: its write waits for the client
        CompletableFuture<Void> second = piece.write(copyOf("cd")).toCompletableFuture();
        CompletableFuture<Void> third = piece.write(copyOf("ef")).toCompletableFuture();
        assertTrue(first.isDone());
        assertFalse(third.isDone());
        consumer.upstream().onBytesConsumed(6);
        assertTrue(second.isDone());
        assertTrue(third.isDone());
        assertEquals("abcdef", consumer.received());
    }

    @Test
    void aFailureOfAStreamedElementFailsTheBody() throws Exception {
        IllegalStateException failure = new IllegalStateException("broken element");
        // the first element: nothing was sent
        BodyStream first = new BodyStream(FACTORY, PropagatedContext.empty(), 1024, false);
        Encoder encoder = new Encoder();
        encoder.encode = element -> ExecutionFlow.just(first.body());
        ExecutionFlow<CloseableByteBody> flow = ResponseStreams.stream(FACTORY, new Elements(List.of("streamed")), encoder, 1024);
        first.fail(failure);
        ExecutionException error = assertThrows(ExecutionException.class, () -> flow.toCompletableFuture().get(10, TimeUnit.SECONDS));
        assertSame(failure, error.getCause());
        // a later element: the stream fails
        BodyStream later = new BodyStream(FACTORY, PropagatedContext.empty(), 1024, false);
        Encoder laterEncoder = new Encoder();
        laterEncoder.encode = element -> element.equals("streamed")
            ? ExecutionFlow.just(later.body())
            : ExecutionFlow.just(FACTORY.copyOf(element + ",", StandardCharsets.UTF_8));
        Consumer consumer = new Consumer(true);
        consumer.subscribe(ResponseStreams.stream(FACTORY, new Elements(List.of("1", "streamed")), laterEncoder, 1024));
        later.fail(failure);
        assertEquals("1,", consumer.received());
        assertSame(failure, consumer.error);
    }

    @Test
    void theClientLeavingDiscardsTheStreamedElement() throws Exception {
        BodyStream piece = new BodyStream(FACTORY, PropagatedContext.empty(), 1024, false);
        AtomicReference<@Nullable Throwable> pieceClosed = new AtomicReference<>();
        AtomicBoolean pieceEnded = new AtomicBoolean();
        piece.onClose(cause -> {
            pieceClosed.set(cause);
            pieceEnded.set(true);
        });
        piece.write(copyOf("ab"));
        Encoder encoder = new Encoder();
        encoder.encode = element -> ExecutionFlow.just(piece.body());
        Elements elements = new Elements(List.of("streamed", "never"));
        Consumer consumer = new Consumer(true);
        consumer.subscribe(ResponseStreams.stream(FACTORY, elements, encoder, 1024));
        consumer.upstream().allowDiscard();
        assertTrue(pieceEnded.get(), "the element is discarded");
        assertInstanceOf(ConnectionClosedException.class, pieceClosed.get());
        assertTrue(elements.closed.get());
        assertEquals(List.of("streamed"), elements.pulled);
    }

    @Test
    void elementsThatFailToCloseAreDiscardedQuietly() {
        BodyElements<String> elements = BodyElements.of(() -> CompletableFuture.completedFuture(Optional.empty()), () -> {
            throw new IllegalStateException("cannot close");
        });
        assertDoesNotThrow(() -> ResponseStreams.discard(elements));
    }

    private static Throwable firstFailure(BodyElements<?> elements, Encoder encoder) {
        ExecutionFlow<CloseableByteBody> flow = ResponseStreams.stream(FACTORY, elements, encoder, 1024);
        ExecutionException error = assertThrows(ExecutionException.class, () -> flow.toCompletableFuture().get(10, TimeUnit.SECONDS));
        return error.getCause();
    }

    private static Consumer read(ExecutionFlow<CloseableByteBody> flow) throws Exception {
        Consumer consumer = new Consumer(true);
        consumer.subscribe(flow);
        return consumer;
    }

    private static CompletableFuture<Optional<String>> read(List<CompletableFuture<Optional<String>>> reads, int index) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            synchronized (reads) {
                if (reads.size() > index) {
                    return reads.get(index);
                }
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("no read " + index);
    }

    private static ReadBuffer copyOf(String text) {
        return FACTORY.readBufferFactory().copyOf(text, StandardCharsets.UTF_8);
    }

    /**
     * Elements of a list, recording the reads and the close.
     */
    private static final class Elements implements BodyElements<String> {
        private final List<String> values;
        private final List<String> pulled = new ArrayList<>();
        private final AtomicBoolean closed = new AtomicBoolean();

        Elements(List<String> values) {
            this.values = values;
        }

        @Override
        public synchronized CompletionStage<Optional<String>> next() {
            if (pulled.size() == values.size()) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            String value = values.get(pulled.size());
            pulled.add(value);
            return CompletableFuture.completedFuture(Optional.of(value));
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    /**
     * Encodes an element as its text and a comma, the end as {@code end}, or {@code none} without
     * elements.
     */
    private static final class Encoder implements ResponseStreams.ElementEncoder {
        private final AtomicBoolean closed = new AtomicBoolean();
        private Function<Object, ExecutionFlow<CloseableByteBody>> encode = element ->
            ExecutionFlow.just(FACTORY.copyOf(element + ",", StandardCharsets.UTF_8));
        private Function<Boolean, @Nullable ReadBuffer> end = none -> copyOf(none ? "none" : "end");

        @Override
        public ExecutionFlow<CloseableByteBody> encode(Object element) {
            return encode.apply(element);
        }

        @Override
        public @Nullable ReadBuffer end(boolean none) {
            return end.apply(none);
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    /**
     * The connection: takes what it is given, and reports it as consumed unless told otherwise.
     */
    private static final class Consumer implements BufferConsumer {
        private final ByteArrayOutputStream received = new ByteArrayOutputStream();
        private final boolean consume;
        private BufferConsumer.@Nullable Upstream upstream;
        private volatile boolean complete;
        private volatile @Nullable Throwable error;

        Consumer(boolean consume) {
            this.consume = consume;
        }

        void subscribe(ExecutionFlow<CloseableByteBody> flow) throws Exception {
            CloseableByteBody body = flow.toCompletableFuture().get(10, TimeUnit.SECONDS);
            BufferConsumer.Upstream u = ((BaseStreamingByteBody<?>) body).primary(this);
            synchronized (this) {
                upstream = u;
            }
            u.start();
            if (consume) {
                u.disregardBackpressure();
            }
        }

        synchronized BufferConsumer.Upstream upstream() {
            BufferConsumer.Upstream u = upstream;
            if (u == null) {
                throw new IllegalStateException("not subscribed");
            }
            return u;
        }

        synchronized String received() {
            return received.toString(StandardCharsets.UTF_8);
        }

        @Override
        public void add(ReadBuffer rb) {
            try (rb) {
                synchronized (this) {
                    received.writeBytes(rb.toArray());
                }
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
