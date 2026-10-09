package io.micronaut.http.body;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * A body made of pulled elements, see {@link ByteBodyFactory#adapt(BodyElements)}, that is
 * discarded while an element is read, and a body whose length is not the one it was given.
 */
class ByteBodyElementsEdgeCaseTest {

    private static final ByteBodyFactory FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
    private static final ReadBufferFactory BUFFERS = ReadBufferFactory.getJdkFactory();

    @Test
    void aPieceThatArrivesAfterTheBodyWasDiscardedIsClosed() {
        PendingElements elements = new PendingElements();
        CloseableByteBody body = FACTORY.adapt(elements);
        AtomicBoolean subscribed = new AtomicBoolean();
        body.toReadBufferPublisher().subscribe(new Subscriber<>() {
            Subscription subscription;

            @Override
            public void onSubscribe(Subscription s) {
                subscription = s;
                subscribed.set(true);
                s.request(1);
                // discarded while the first element is read
                s.cancel();
            }

            @Override
            public void onNext(ReadBuffer readBuffer) {
                readBuffer.close();
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
        Assertions.assertTrue(subscribed.get());
        Assertions.assertNotNull(elements.pending, "an element is read");
        Assertions.assertTrue(elements.closed.get(), "discarding the body closes the elements");

        // the piece the read was waiting for arrives anyway
        ReadBuffer late = buffer("late");
        elements.pending.complete(Optional.of(late));
        Assertions.assertThrows(IllegalStateException.class, late::readable, "the late piece is closed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "abcdefg"})
    void aPublishedBodyWhoseLengthIsWrongFails(String content) {
        CloseableByteBody body = FACTORY.adapt(Flux.just(buffer(content)), OptionalLong.of(5));
        assertFails(body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "abcdefg"})
    void aBodyOfPulledElementsWhoseLengthIsWrongFails(String content) {
        CloseableByteBody body = FACTORY.adapt(new ListElements(buffer(content)), OptionalLong.of(5));
        assertFails(body);
    }

    @Test
    void aPublishedBodyWhoseLengthIsWrongFailsItsReader() {
        Publisher<ReadBuffer> pieces = Flux.just(buffer("ab"), buffer("c"));
        CloseableByteBody body = FACTORY.adapt(pieces, OptionalLong.of(5));
        ExecutionException e = Assertions.assertThrows(ExecutionException.class, () ->
            Flux.from(body.toReadBufferPublisher()).doOnNext(ReadBuffer::close).then().toFuture().get(10, TimeUnit.SECONDS));
        Assertions.assertInstanceOf(IllegalStateException.class, e.getCause());
    }

    private static void assertFails(CloseableByteBody body) {
        ExecutionException e = Assertions.assertThrows(ExecutionException.class, () -> {
            try {
                body.buffer().get(10, TimeUnit.SECONDS).close();
            } catch (TimeoutException timeout) {
                Assertions.fail("the body hangs");
            }
        });
        Assertions.assertInstanceOf(IllegalStateException.class, e.getCause());
        Assertions.assertTrue(e.getCause().getMessage().contains("Content-Length"), e.getCause().getMessage());
    }

    @Test
    void rebufferingAnAvailableBodyPreservesItsLength() throws Exception {
        try (CloseableByteBody original = FACTORY.copyOf("hello", StandardCharsets.UTF_8);
             CloseableByteBody streaming = FACTORY.toStreaming(original)) {
            Assertions.assertEquals(OptionalLong.of(5), streaming.expectedLength());
            try (CloseableAvailableByteBody buffered = streaming.buffer().get(10, TimeUnit.SECONDS)) {
                Assertions.assertEquals("hello", buffered.toString(StandardCharsets.UTF_8));
            }
        }
    }

    private static ReadBuffer buffer(String s) {
        return BUFFERS.copyOf(s, StandardCharsets.UTF_8);
    }

    /**
     * Elements whose first read waits until the test completes it.
     */
    private static final class PendingElements implements BodyElements<ReadBuffer> {
        final AtomicBoolean closed = new AtomicBoolean();
        CompletableFuture<Optional<ReadBuffer>> pending;

        @Override
        public CompletionStage<Optional<ReadBuffer>> next() {
            pending = new CompletableFuture<>();
            return pending;
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
            closed.set(true);
        }
    }

    /**
     * Elements from a list, one per {@link #next()}.
     */
    private static final class ListElements implements BodyElements<ReadBuffer> {
        final ArrayDeque<ReadBuffer> buffers;

        ListElements(ReadBuffer... buffers) {
            this.buffers = new ArrayDeque<>(List.of(buffers));
        }

        @Override
        public CompletionStage<Optional<ReadBuffer>> next() {
            return CompletableFuture.completedFuture(Optional.ofNullable(buffers.poll()));
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
            ReadBuffer b;
            while ((b = buffers.poll()) != null) {
                b.close();
            }
        }
    }
}
