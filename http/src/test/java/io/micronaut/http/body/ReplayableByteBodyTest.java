package io.micronaut.http.body;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.exceptions.BufferLengthExceededException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * {@link ByteBodyFactory#replayable(CloseableByteBody, long)}: every reader gets the bytes from
 * the start, up to the limit.
 */
@Timeout(10)
class ReplayableByteBodyTest {
    private static final ByteBodyFactory FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    private static ReadBuffer buf(String text) {
        return FACTORY.readBufferFactory().copyOf(text, StandardCharsets.UTF_8);
    }

    private static CloseableByteBody streamed(String... parts) {
        return FACTORY.adapt(Flux.fromArray(parts).map(ReplayableByteBodyTest::buf));
    }

    /**
     * Read a body as it streams, like a client that sends it.
     */
    private static String read(CloseableByteBody body) {
        return Flux.from(body.toReadBufferPublisher())
            .map(b -> {
                try (b) {
                    return b.toString(StandardCharsets.UTF_8);
                }
            })
            .reduce("", String::concat)
            .block(java.time.Duration.ofSeconds(5));
    }

    @Test
    void everyReaderGetsTheWholeBody() throws Exception {
        try (ReplayableByteBody replayable = FACTORY.replayable(streamed("abc", "def"), 100)) {
            Assertions.assertEquals("abcdef", read(replayable.next()));
            Assertions.assertTrue(replayable.isReplayable());
            Assertions.assertEquals("abcdef", read(replayable.next()));
            Assertions.assertEquals("abcdef", read(replayable.next()));
        }
    }

    @Test
    void aReaderThatStartsWhileTheBytesArriveGetsTheBytesFromTheStart() throws Exception {
        Sinks.Many<ReadBuffer> sink = Sinks.many().unicast().onBackpressureBuffer();
        try (ReplayableByteBody replayable = FACTORY.replayable(FACTORY.adapt(sink.asFlux()), 100)) {
            CompletableFuture<String> first = new CompletableFuture<>();
            StringBuilder firstText = new StringBuilder();
            Flux.from(replayable.next().toReadBufferPublisher())
                .doOnNext(b -> {
                    firstText.append(b.toString(StandardCharsets.UTF_8));
                    b.close();
                })
                .doOnComplete(() -> first.complete(firstText.toString()))
                .subscribe();
            sink.tryEmitNext(buf("abc")).orThrow();
            CloseableByteBody second = replayable.next();
            sink.tryEmitNext(buf("def")).orThrow();
            sink.tryEmitComplete().orThrow();
            Assertions.assertEquals("abcdef", first.get(5, TimeUnit.SECONDS));
            Assertions.assertEquals("abcdef", read(second));
        }
    }

    @Test
    void aBodyOverTheLimitIsReadOnce() throws Exception {
        try (ReplayableByteBody replayable = FACTORY.replayable(streamed("abc", "def"), 4)) {
            Assertions.assertTrue(replayable.isReplayable(), "the length is not known yet");
            Assertions.assertEquals("abcdef", read(replayable.next()));
            Assertions.assertFalse(replayable.isReplayable());
            Assertions.assertThrows(BufferLengthExceededException.class, replayable::next);
        }
    }

    @Test
    void aBodyWhoseKnownLengthIsOverTheLimitIsNotReplayable() throws Exception {
        try (ReplayableByteBody replayable = FACTORY.replayable(FACTORY.adapt(Flux.just(buf("abc"), buf("def")), OptionalLong.of(6)), 4)) {
            Assertions.assertFalse(replayable.isReplayable());
            Assertions.assertEquals("abcdef", read(replayable.next()));
        }
    }

    @Test
    void aBodyWhoseBytesAreAllThereIsAlwaysReplayable() throws Exception {
        try (ReplayableByteBody replayable = FACTORY.replayable(FACTORY.adapt("abcdef".getBytes(StandardCharsets.UTF_8)), 1)) {
            Assertions.assertTrue(replayable.isReplayable());
            Assertions.assertEquals("abcdef", read(replayable.next()));
            Assertions.assertEquals("abcdef", read(replayable.next()));
        }
    }

    @Test
    void aClosedBodyCannotBeRead() {
        ReplayableByteBody replayable = FACTORY.replayable(streamed("abc"), 100);
        replayable.close();
        Assertions.assertFalse(replayable.isReplayable());
        Assertions.assertThrows(IllegalStateException.class, replayable::next);
    }

    /**
     * A reader that consumes the bytes as they arrive is not charged for the bytes the body
     * keeps for the next readers.
     */
    @Test
    void aReaderThatDrainsAsTheBytesArriveIsNotChargedForTheKeptBytes() throws Exception {
        Sinks.Many<ReadBuffer> sink = Sinks.many().unicast().onBackpressureBuffer();
        try (ReplayableByteBody replayable = FACTORY.replayable(FACTORY.adapt(sink.asFlux()), 4)) {
            CompletableFuture<String> first = new CompletableFuture<>();
            StringBuilder firstText = new StringBuilder();
            Flux.from(replayable.next().toReadBufferPublisher())
                .doOnNext(b -> {
                    firstText.append(b.toString(StandardCharsets.UTF_8));
                    b.close();
                })
                .doOnError(first::completeExceptionally)
                .doOnComplete(() -> first.complete(firstText.toString()))
                .subscribe();
            sink.tryEmitNext(buf("abc")).orThrow();
            sink.tryEmitNext(buf("def")).orThrow();
            sink.tryEmitComplete().orThrow();
            Assertions.assertEquals("abcdef", first.get(5, TimeUnit.SECONDS));
            Assertions.assertFalse(replayable.isReplayable());
        }
    }

    @Test
    void aStreamedFirstReadKeepsTheBodyReplayable() {
        try (ReplayableByteBody replayable = FACTORY.replayable(streamed("abc", "def", "ghi"), 10)) {
            Assertions.assertEquals("abcdefghi", read(replayable.next()));
            Assertions.assertTrue(replayable.isReplayable());
            Assertions.assertEquals("abcdefghi", read(replayable.next()));
        }
    }

    /**
     * A reader that starts after the bytes arrived gets them in one piece: it is charged for them
     * once, not on top of what the body keeps.
     */
    @Test
    void aStreamedReplayOfABodyOverHalfTheLimit() throws Exception {
        try (ReplayableByteBody replayable = FACTORY.replayable(streamed("abc", "def", "ghi"), 10)) {
            try (CloseableAvailableByteBody available = replayable.next().buffer().get(5, TimeUnit.SECONDS)) {
                Assertions.assertEquals("abcdefghi", available.toString(StandardCharsets.UTF_8));
            }
            Assertions.assertTrue(replayable.isReplayable());
            Assertions.assertEquals("abcdefghi", read(replayable.next()));
            Assertions.assertEquals("abcdefghi", read(replayable.next()));
        }
    }

    @Test
    void aCancelledReaderDoesNotShrinkTheLimit() {
        try (ReplayableByteBody replayable = FACTORY.replayable(streamed("abc", "def", "ghi"), 10)) {
            Assertions.assertEquals("abc", Flux.from(replayable.next().toReadBufferPublisher())
                .take(1)
                .map(b -> {
                    try (b) {
                        return b.toString(StandardCharsets.UTF_8);
                    }
                })
                .blockFirst(java.time.Duration.ofSeconds(5)));
            Assertions.assertEquals("abcdefghi", read(replayable.next()));
            Assertions.assertEquals("abcdefghi", read(replayable.next()));
        }
    }

    @Test
    void aFailedBodyIsNotReplayable() {
        try (ReplayableByteBody replayable = FACTORY.replayable(FACTORY.adapt(Flux.concat(Flux.just(buf("abc")), Flux.error(new java.io.IOException("reset")))), 100)) {
            Assertions.assertThrows(Exception.class, () -> read(replayable.next()));
            Assertions.assertFalse(replayable.isReplayable());
        }
    }

    @Test
    void aBodyWhoseKnownLengthIsOverTheLimitIsReadOnce() {
        try (ReplayableByteBody replayable = FACTORY.replayable(FACTORY.adapt(Flux.just(buf("abc"), buf("def")), OptionalLong.of(6)), 4)) {
            Assertions.assertEquals("abcdef", read(replayable.next()));
            Assertions.assertThrows(BufferLengthExceededException.class, replayable::next);
        }
    }
}
