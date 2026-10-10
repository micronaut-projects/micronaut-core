package io.micronaut.http.body.stream;

import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.PieceReader;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Operators;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The publisher bridge over a piece reader and the publisher of {@link BodyElements}, when the
 * subscriber requests and cancels on other threads than the input emits on.
 */
class PieceReaderPublisherConcurrencyTest {

    @Test
    void passesDownstreamContextToTheInput() {
        AtomicReference<String> tenant = new AtomicReference<>();
        Publisher<String> lines = PieceReaders.publisher(Flux.deferContextual(context -> {
            tenant.set(context.getOrDefault("tenant", "missing"));
            return Flux.just(piece("value\n"));
        }), new LineReader());
        assertEquals(List.of("value"), Flux.from(lines)
            .contextWrite(reactor.util.context.Context.of("tenant", "expected"))
            .collectList().block());
        assertEquals("expected", tenant.get());
    }

    @Test
    void requestsOnAnotherThreadNeverAskForASecondPiece() throws Exception {
        try (ExecutorService requester = Executors.newSingleThreadExecutor()) {
            for (int round = 0; round < 200; round++) {
                int count = 500;
                Flux<ReadBuffer> input = Flux.range(0, count)
                    .map(i -> piece(i + "\n"))
                    .publishOn(Schedulers.parallel(), 1);
                Publisher<String> lines = PieceReaders.publisher(input, new LineReader());
                CountDownLatch done = new CountDownLatch(1);
                AtomicInteger received = new AtomicInteger();
                AtomicReference<Throwable> failure = new AtomicReference<>();
                lines.subscribe(new Subscriber<>() {
                    Subscription s;

                    @Override
                    public void onSubscribe(Subscription s) {
                        this.s = s;
                        s.request(2);
                    }

                    @Override
                    public void onNext(String s1) {
                        received.incrementAndGet();
                        // the demand arrives on another thread than the pieces
                        requester.execute(() -> s.request(1));
                    }

                    @Override
                    public void onError(Throwable t) {
                        failure.set(t);
                        done.countDown();
                    }

                    @Override
                    public void onComplete() {
                        done.countDown();
                    }
                });
                assertTrue(done.await(30, TimeUnit.SECONDS), "round " + round + " did not end");
                int r = round;
                assertNull(failure.get(), () -> "round " + r + " failed: " + failure.get());
                assertEquals(count, received.get());
            }
        }
    }

    @Test
    void anElementTheEndCompletedIsReleasedWhenCancelled() {
        List<Counted> created = new ArrayList<>();
        Publisher<Counted> elements = PieceReaders.publisher(Flux.just(piece("a\nb")), new CountedLineReader(created));
        AtomicReference<Subscription> subscription = new AtomicReference<>();
        List<Counted> received = new ArrayList<>();
        elements.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription s) {
                subscription.set(s);
            }

            @Override
            public void onNext(Counted counted) {
                received.add(counted);
            }

            @Override
            public void onError(Throwable t) {
                // nothing to do in this test
            }

            @Override
            public void onComplete() {
                // nothing to do in this test
            }
        });
        subscription.get().request(1);
        assertEquals(1, received.size());
        // "b" is completed by the end of the input, and waits for demand
        assertEquals(2, created.size());
        subscription.get().cancel();
        assertEquals(0, created.get(1).refCnt.get(), "the element the end completed was not released");
    }

    @Test
    void theDiscardHookReleasesReferenceCountedAndForeignObjects() {
        AtomicReference<CoreSubscriber<? super ReadBuffer>> inputSubscriber = new AtomicReference<>();
        @SuppressWarnings("unchecked")
        Publisher<ReadBuffer> input = s -> {
            inputSubscriber.set((CoreSubscriber<? super ReadBuffer>) s);
            s.onSubscribe(Operators.emptySubscription());
        };
        List<Object> foreign = new ArrayList<>();
        Publisher<String> lines = new PieceReaderPublisher<>(input, Function.identity(), ReadBuffer::close, new LineReader(), foreign::add);
        lines.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription s) {
                // nothing to do in this test
            }

            @Override
            public void onNext(String s) {
                // nothing to do in this test
            }

            @Override
            public void onError(Throwable t) {
                // nothing to do in this test
            }

            @Override
            public void onComplete() {
                // nothing to do in this test
            }
        });
        Counted counted = new Counted("x");
        Object other = new Object();
        Operators.onDiscard(counted, inputSubscriber.get().currentContext());
        Operators.onDiscard(other, inputSubscriber.get().currentContext());
        assertEquals(0, counted.refCnt.get());
        assertEquals(List.of(other), foreign);
    }

    @Test
    void requestZeroAfterCompletionSignalsNothing() {
        List<String> signals = new ArrayList<>();
        AtomicReference<Subscription> subscription = new AtomicReference<>();
        new BodyElementsPublisher<>(new QueueElements(List.of(1))).subscribe(new Subscriber<Integer>() {
            @Override
            public void onSubscribe(Subscription s) {
                subscription.set(s);
            }

            @Override
            public void onNext(Integer integer) {
                signals.add("next");
            }

            @Override
            public void onError(Throwable t) {
                signals.add("error");
            }

            @Override
            public void onComplete() {
                signals.add("complete");
            }
        });
        subscription.get().request(5);
        assertEquals(List.of("next", "complete"), signals);
        subscription.get().request(0);
        assertEquals(List.of("next", "complete"), signals, "§1.7: no signal after the terminal one");
    }

    @Test
    void cancelRacingRequestNeverFailsTheRequest() throws Exception {
        try (ExecutorService canceller = Executors.newSingleThreadExecutor()) {
            for (int round = 0; round < 2000; round++) {
                StrictElements elements = new StrictElements();
                AtomicReference<Subscription> subscription = new AtomicReference<>();
                AtomicReference<Throwable> failure = new AtomicReference<>();
                new BodyElementsPublisher<>(elements).subscribe(new Subscriber<Integer>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        subscription.set(s);
                    }

                    @Override
                    public void onNext(Integer integer) {
                        // nothing to do in this test
                    }

                    @Override
                    public void onError(Throwable t) {
                        failure.set(t);
                    }

                    @Override
                    public void onComplete() {
                        // nothing to do in this test
                    }
                });
                CountDownLatch start = new CountDownLatch(1);
                CompletableFuture<Void> cancelled = CompletableFuture.runAsync(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    subscription.get().cancel();
                }, canceller);
                start.countDown();
                for (int i = 0; i < 20; i++) {
                    // never throws, even when the elements are closed meanwhile
                    subscription.get().request(1);
                }
                cancelled.get(10, TimeUnit.SECONDS);
                assertNull(failure.get(), () -> "round failed: " + failure.get());
                assertFalse(elements.overlapped.get(), "a read and close overlapped");
                assertTrue(elements.closed);
            }
        }
    }

    @Test
    void anElementReadAfterCancellingIsReleased() {
        CompletableFuture<Optional<Counted>> pending = new CompletableFuture<>();
        BodyElements<Counted> elements = new BodyElements<>() {
            @Override
            public CompletionStage<Optional<Counted>> next() {
                return pending;
            }

            @Override
            public CompletionStage<Void> forEach(Function<? super Counted, ? extends CompletionStage<?>> consumer) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletionStage<Void> closeAsync() {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void close() {
                // nothing to do in this test
            }
        };
        AtomicReference<Subscription> subscription = new AtomicReference<>();
        new BodyElementsPublisher<>(elements).subscribe(new Subscriber<Counted>() {
            @Override
            public void onSubscribe(Subscription s) {
                subscription.set(s);
            }

            @Override
            public void onNext(Counted counted) {
                // nothing to do in this test
            }

            @Override
            public void onError(Throwable t) {
                // nothing to do in this test
            }

            @Override
            public void onComplete() {
                // nothing to do in this test
            }
        });
        subscription.get().request(1);
        subscription.get().cancel();
        Counted late = new Counted("late");
        pending.complete(Optional.of(late));
        assertEquals(0, late.refCnt.get());
    }

    private static ReadBuffer piece(String text) {
        return ReadBufferFactory.getJdkFactory().adapt(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Lines.
     */
    private static final class LineReader implements PieceReader<String> {
        private final StringBuilder pending = new StringBuilder();
        private final ArrayDeque<String> lines = new ArrayDeque<>();

        @Override
        public void read(ReadBuffer piece) {
            try (piece) {
                pending.append(piece.toString(StandardCharsets.UTF_8));
            }
            int end;
            while ((end = pending.indexOf("\n")) >= 0) {
                lines.add(pending.substring(0, end));
                pending.delete(0, end + 1);
            }
        }

        @Override
        public void complete() {
            // nothing to do in this test
        }

        @Override
        public @Nullable String poll() {
            return lines.poll();
        }

        @Override
        public void close() {
            lines.clear();
        }
    }

    /**
     * Lines as reference counted elements; the bytes after the last line ending are a line at
     * the end.
     */
    private static final class CountedLineReader implements PieceReader<Counted> {
        private final List<Counted> created;
        private final StringBuilder pending = new StringBuilder();
        private final ArrayDeque<Counted> lines = new ArrayDeque<>();

        CountedLineReader(List<Counted> created) {
            this.created = created;
        }

        @Override
        public void read(ReadBuffer piece) {
            try (piece) {
                pending.append(piece.toString(StandardCharsets.UTF_8));
            }
            int end;
            while ((end = pending.indexOf("\n")) >= 0) {
                add(pending.substring(0, end));
                pending.delete(0, end + 1);
            }
        }

        private void add(String line) {
            Counted counted = new Counted(line);
            created.add(counted);
            lines.add(counted);
        }

        @Override
        public void complete() {
            if (!pending.isEmpty()) {
                add(pending.toString());
                pending.setLength(0);
            }
        }

        @Override
        public @Nullable Counted poll() {
            return lines.poll();
        }

        @Override
        public void close() {
            Counted counted;
            while ((counted = lines.poll()) != null) {
                counted.release();
            }
        }
    }

    static final class Counted implements ReferenceCounted {
        final String value;
        final AtomicInteger refCnt = new AtomicInteger(1);

        Counted(String value) {
            this.value = value;
        }

        @Override
        public ReferenceCounted retain() {
            refCnt.incrementAndGet();
            return this;
        }

        @Override
        public boolean release() {
            return refCnt.decrementAndGet() == 0;
        }
    }

    /**
     * Elements from a list.
     */
    private static final class QueueElements implements BodyElements<Integer> {
        private final ArrayDeque<Integer> queue;

        QueueElements(List<Integer> values) {
            queue = new ArrayDeque<>(values);
        }

        @Override
        public CompletionStage<Optional<Integer>> next() {
            return CompletableFuture.completedFuture(Optional.ofNullable(queue.poll()));
        }

        @Override
        public CompletionStage<Void> forEach(Function<? super Integer, ? extends CompletionStage<?>> consumer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<Void> closeAsync() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
            // nothing to do in this test
        }
    }

    /**
     * Endless elements that follow the contract strictly: a read after closing throws, and a
     * read or a close that overlaps another call is recorded.
     */
    private static final class StrictElements implements BodyElements<Integer> {
        final AtomicBoolean overlapped = new AtomicBoolean();
        private final AtomicBoolean inCall = new AtomicBoolean();
        volatile boolean closed;

        @Override
        public CompletionStage<Optional<Integer>> next() {
            enter();
            try {
                if (closed) {
                    throw new IllegalStateException("The elements were closed");
                }
                Thread.onSpinWait();
                return CompletableFuture.completedFuture(Optional.of(1));
            } finally {
                inCall.set(false);
            }
        }

        private void enter() {
            if (!inCall.compareAndSet(false, true)) {
                overlapped.set(true);
            }
        }

        @Override
        public CompletionStage<Void> forEach(Function<? super Integer, ? extends CompletionStage<?>> consumer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<Void> closeAsync() {
            close();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
            enter();
            try {
                closed = true;
                Thread.onSpinWait();
            } finally {
                inCall.set(false);
            }
        }
    }
}
