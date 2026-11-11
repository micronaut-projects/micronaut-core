package io.micronaut.http.body.stream;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.PieceReader;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cursor over the elements a piece reader reads from a body.
 */
class ByteBodyElementsTest {
    private static final ByteBodyFactory BODIES = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    @Test
    void aCompletionThatRacesWithTheDeliveryOfAnElementDoesNotEndTheRead() {
        for (int i = 0; i < 200; i++) {
            Sinks.Many<ReadBuffer> input = Sinks.many().unicast().onBackpressureBuffer();
            RacingReader reader = new RacingReader();
            try (ByteBodyElements<String> elements = new ByteBodyElements<>(BODIES.adapt(input.asFlux()), reader, e -> e)) {
                reader.elements = elements;
                var first = elements.next().toCompletableFuture();
                input.tryEmitNext(ReadBufferFactoryHolder.copyOf("a,b"));
                assertEquals(Optional.of("a"), first.join());

                // the completion arrives while the second element is handed to the read
                reader.raceOnPoll = true;
                assertEquals(Optional.of("b"), elements.next().toCompletableFuture().join(), "iteration " + i);
                assertEquals(Optional.empty(), elements.next().toCompletableFuture().join());
            }
        }
    }

    @Test
    void theElementsOfAReceivedPieceAreTakenAtOnce() {
        Sinks.Many<ReadBuffer> input = Sinks.many().unicast().onBackpressureBuffer();
        try (ByteBodyElements<String> elements = new ByteBodyElements<>(BODIES.adapt(input.asFlux()), new RacingReader(), e -> e)) {
            assertEquals(BodyElements.State.PENDING, elements.state());
            var first = elements.next().toCompletableFuture();
            input.tryEmitNext(ReadBufferFactoryHolder.copyOf("a,b,c"));
            assertEquals(Optional.of("a"), first.join());
            assertEquals(BodyElements.State.AVAILABLE, elements.state());
            assertEquals("b", elements.poll());
            var third = elements.next().toCompletableFuture();
            assertTrue(third.isDone());
            assertEquals(Optional.of("c"), third.join());
            assertNull(elements.poll());
            assertEquals(BodyElements.State.PENDING, elements.state());
            input.tryEmitComplete();
            assertEquals(Optional.empty(), elements.next().toCompletableFuture().join());
            assertEquals(BodyElements.State.COMPLETED, elements.state());
        }
    }

    @Test
    void inputDeliveryDoesNotWaitForWorkerDecoding() throws Exception {
        Sinks.Many<ReadBuffer> input = Sinks.many().unicast().onBackpressureBuffer();
        RacingReader reader = new RacingReader();
        try (ByteBodyElements<String> elements = new ByteBodyElements<>(BODIES.adapt(input.asFlux()), reader, e -> e)) {
            var first = elements.next().toCompletableFuture();
            input.tryEmitNext(ReadBufferFactoryHolder.copyOf("a,b"));
            assertEquals(Optional.of("a"), first.get(1, TimeUnit.SECONDS));
            reader.blockOnPoll = true;
            CompletableFuture<String> decoded = CompletableFuture.supplyAsync(elements::poll);
            try {
                assertTrue(reader.decoding.await(1, TimeUnit.SECONDS));
                CompletableFuture.runAsync(() -> elements.onNext(ReadBufferFactoryHolder.copyOf("c")))
                    .get(1, TimeUnit.SECONDS);
            } finally {
                reader.resume.countDown();
            }
            assertEquals("b", decoded.get(1, TimeUnit.SECONDS));
            assertEquals(Optional.of("c"), elements.next().toCompletableFuture().get(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void onePieceIsReceivedAhead() {
        Sinks.Many<ReadBuffer> input = Sinks.many().unicast().onBackpressureBuffer();
        for (String piece : List.of("a", "b", "c", "d", "e")) {
            input.tryEmitNext(ReadBufferFactoryHolder.copyOf(piece));
        }
        RacingReader reader = new RacingReader();
        try (ByteBodyElements<String> elements = new ByteBodyElements<>(BODIES.adapt(input.asFlux()), reader, e -> e)) {
            assertEquals(0, reader.pieces);
            assertEquals(Optional.of("a"), elements.next().toCompletableFuture().join());
            // the piece of the read, and one ahead
            assertEquals(2, reader.pieces);
            assertEquals(Optional.of("b"), elements.next().toCompletableFuture().join());
            // taken from the piece received ahead: no other piece is requested
            assertEquals(2, reader.pieces);
            assertEquals(Optional.of("c"), elements.next().toCompletableFuture().join());
            assertEquals(4, reader.pieces);
        }
    }

    private static final class ReadBufferFactoryHolder {
        static ReadBuffer copyOf(String text) {
            return io.micronaut.core.io.buffer.ReadBufferFactory.getJdkFactory().copyOf(text, StandardCharsets.UTF_8);
        }
    }

    /**
     * One element per comma separated word. When asked to, it completes the elements from
     * another thread while it hands out an element, and lets that thread wait for the lock of
     * the elements before it returns.
     */
    private static final class RacingReader implements PieceReader<String> {
        private final ArrayDeque<String> words = new ArrayDeque<>();
        ByteBodyElements<String> elements;
        volatile boolean raceOnPoll;
        volatile boolean blockOnPoll;
        final CountDownLatch decoding = new CountDownLatch(1);
        final CountDownLatch resume = new CountDownLatch(1);
        int pieces;

        @Override
        public void read(ReadBuffer piece) {
            pieces++;
            try (piece) {
                words.addAll(Arrays.asList(piece.toString(StandardCharsets.UTF_8).split(",")));
            }
        }

        @Override
        public void complete() {
            // the words are complete with the last piece
        }

        @Override
        public @Nullable String poll() {
            String word = words.poll();
            if (word != null && blockOnPoll) {
                blockOnPoll = false;
                decoding.countDown();
                try {
                    assertTrue(resume.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
            if (word != null && raceOnPoll) {
                raceOnPoll = false;
                Thread completer = new Thread(elements::onComplete);
                completer.start();
                try {
                    completer.join(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
                assertTrue(!completer.isAlive(), "Input completion must not wait for decoding");
            }
            return word;
        }

        @Override
        public void close() {
            words.clear();
        }
    }
}
