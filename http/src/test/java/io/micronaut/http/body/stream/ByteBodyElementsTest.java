package io.micronaut.http.body.stream;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.PieceReader;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

        @Override
        public void read(ReadBuffer piece) {
            try (piece) {
                words.addAll(Arrays.asList(piece.toString(StandardCharsets.UTF_8).split(",")));
            }
        }

        @Override
        public void complete() {
        }

        @Override
        public @Nullable String poll() {
            String word = words.poll();
            if (word != null && raceOnPoll) {
                raceOnPoll = false;
                Thread completer = new Thread(elements::onComplete);
                completer.start();
                // the completer waits for the lock this poll holds
                while (completer.getState() != Thread.State.BLOCKED && completer.isAlive()) {
                    Thread.onSpinWait();
                }
            }
            return word;
        }

        @Override
        public void close() {
            words.clear();
        }
    }
}
