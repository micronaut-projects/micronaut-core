package io.micronaut.http.body;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.body.stream.BodySizeLimits;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * {@link ByteBody#toReadBufferElements()} and
 * {@link ByteBodyFactory#adapt(BodyElements)}: the bytes of a body pulled one piece at a time,
 * and a body made of pieces pulled one at a time.
 */
class ByteBodyElementsApiTest {

    private static final ByteBodyFactory FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
    private static final ReadBufferFactory BUFFERS = ReadBufferFactory.getJdkFactory();

    @Test
    void anAvailableBodyIsOneElement() throws Exception {
        try (BodyElements<ReadBuffer> elements = FACTORY.adapt("hello".getBytes(StandardCharsets.UTF_8)).toReadBufferElements()) {
            Assertions.assertEquals("hello", nextString(elements));
            Assertions.assertTrue(elements.next().toCompletableFuture().get(10, TimeUnit.SECONDS).isEmpty());
        }
    }

    @Test
    void aStreamingBodyIsItsPiecesInOrder() throws Exception {
        CloseableByteBody body = FACTORY.adapt(Flux.just(buffer("a"), buffer(""), buffer("bc"), buffer("d")));
        try (BodyElements<ReadBuffer> elements = body.toReadBufferElements()) {
            List<String> pieces = new ArrayList<>();
            elements.forEach(piece -> {
                try (piece) {
                    pieces.add(piece.toString(StandardCharsets.UTF_8));
                }
                return CompletableFuture.completedFuture(null);
            }).toCompletableFuture().get(10, TimeUnit.SECONDS);
            // an empty piece is not an element
            Assertions.assertEquals(List.of("a", "bc", "d"), pieces);
        }
    }

    @Test
    void closingTheElementsEarlyDiscardsTheRestOfTheBody() throws Exception {
        AtomicBoolean discarded = new AtomicBoolean();
        CloseableByteBody body = FACTORY.adapt(Flux.just(buffer("a"), buffer("b"), buffer("c")).concatWith(Flux.never()),
            BodySizeLimits.UNLIMITED, null, () -> discarded.set(true));
        BodyElements<ReadBuffer> elements = body.toReadBufferElements();
        Assertions.assertEquals("a", nextString(elements));
        elements.closeAsync().toCompletableFuture().get(10, TimeUnit.SECONDS);
        Assertions.assertTrue(discarded.get());
    }

    @Test
    void theElementsTakeOverTheBody() {
        CloseableByteBody body = FACTORY.adapt("hello".getBytes(StandardCharsets.UTF_8));
        BodyElements<ReadBuffer> elements = body.toReadBufferElements();
        // a primary operation: the body was moved into the elements
        Assertions.assertThrows(IllegalStateException.class, body::buffer);
        elements.close();
    }

    @Test
    void aBodyMadeOfPulledElements() throws Exception {
        ListElements elements = new ListElements(buffer("a"), buffer("bc"), buffer("d"));
        try (CloseableByteBody body = FACTORY.adapt(elements)) {
            Assertions.assertEquals(OptionalLong.empty(), body.expectedLength());
            try (CloseableAvailableByteBody available = body.buffer().get(10, TimeUnit.SECONDS)) {
                Assertions.assertEquals("abcd", new String(available.toByteArray(), StandardCharsets.UTF_8));
            }
        }
        Assertions.assertEquals(4, elements.reads.get(), "three elements and the end");
    }

    @Test
    void aBodyMadeOfPulledElementsWithALength() throws Exception {
        try (CloseableByteBody body = FACTORY.adapt(new ListElements(buffer("ab"), buffer("c")), OptionalLong.of(3))) {
            Assertions.assertEquals(OptionalLong.of(3), body.expectedLength());
            try (CloseableAvailableByteBody available = body.buffer().get(10, TimeUnit.SECONDS)) {
                Assertions.assertEquals("abc", new String(available.toByteArray(), StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void aFailedElementFailsTheBody() {
        IllegalStateException failure = new IllegalStateException("element failed");
        ListElements elements = new ListElements(buffer("a"));
        elements.failure = failure;
        CloseableByteBody body = FACTORY.adapt(elements);
        ExecutionException e = Assertions.assertThrows(ExecutionException.class, () -> body.buffer().get(10, TimeUnit.SECONDS));
        Assertions.assertSame(failure, e.getCause());
    }

    @Test
    void discardingABodyMadeOfPulledElementsClosesThem() {
        ListElements elements = new ListElements(buffer("a"), buffer("b"));
        CloseableByteBody body = FACTORY.adapt(elements);
        body.close();
        Assertions.assertTrue(elements.closed.get());
    }

    @Test
    void aBodyReadAsElementsCanBeMadeIntoAnotherBody() throws Exception {
        CloseableByteBody source = FACTORY.adapt(Flux.just(buffer("x"), buffer("yz")));
        try (CloseableByteBody copy = FACTORY.adapt(source.toReadBufferElements());
             CloseableAvailableByteBody available = copy.buffer().get(10, TimeUnit.SECONDS)) {
            Assertions.assertEquals("xyz", new String(available.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    private static String nextString(BodyElements<ReadBuffer> elements) throws Exception {
        Optional<ReadBuffer> piece = elements.next().toCompletableFuture().get(10, TimeUnit.SECONDS);
        try (ReadBuffer buffer = piece.orElseThrow()) {
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    private static ReadBuffer buffer(String s) {
        return BUFFERS.copyOf(s, StandardCharsets.UTF_8);
    }

    /**
     * Elements from a list, one per {@link #next()}, failing at the end if a failure is set.
     */
    private static final class ListElements implements BodyElements<ReadBuffer> {
        final ArrayDeque<ReadBuffer> buffers;
        final AtomicInteger reads = new AtomicInteger();
        final AtomicBoolean closed = new AtomicBoolean();
        Throwable failure;

        ListElements(ReadBuffer... buffers) {
            this.buffers = new ArrayDeque<>(List.of(buffers));
        }

        @Override
        public CompletionStage<Optional<ReadBuffer>> next() {
            reads.incrementAndGet();
            ReadBuffer next = buffers.poll();
            if (next == null && failure != null) {
                return CompletableFuture.failedFuture(failure);
            }
            return CompletableFuture.completedFuture(Optional.ofNullable(next));
        }

        @Override
        public CompletionStage<Void> forEach(Function<? super ReadBuffer, ? extends CompletionStage<?>> consumer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<Void> closeAsync() {
            close();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                ReadBuffer b;
                while ((b = buffers.poll()) != null) {
                    b.close();
                }
            }
        }
    }
}
