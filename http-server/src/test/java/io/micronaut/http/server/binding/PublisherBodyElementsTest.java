package io.micronaut.http.server.binding;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The pull cursor over the decoded elements of a body, and the whole body read like the content
 * of a form field: nothing is read before it is asked for, one element at a time.
 */
class PublisherBodyElementsTest {

    private static final ByteBodyFactory BODY_FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    @TempDir
    Path directory;

    private static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static Throwable failure(CompletionStage<?> stage) {
        try {
            stage.toCompletableFuture().join();
        } catch (CompletionException e) {
            return e.getCause();
        } catch (CancellationException e) {
            return e;
        }
        return fail("completed normally");
    }

    @Test
    void nothingIsReadBeforeTheFirstElementIsAskedFor() {
        ElementPublisher publisher = new ElementPublisher();
        AtomicInteger created = new AtomicInteger();
        BodyElements<String> elements = new PublisherBodyElements<>(() -> {
            created.incrementAndGet();
            return publisher;
        }, () -> { });
        assertEquals(0, created.get());
        CompletionStage<Optional<String>> first = elements.next();
        assertEquals(1, created.get());
        assertEquals(1, publisher.requested, "one element per read");
        assertFalse(first.toCompletableFuture().isDone());
        publisher.emit("a");
        assertEquals(Optional.of("a"), join(first));
        assertEquals(1, publisher.requested, "nothing more is requested before the next read");

        CompletionStage<Optional<String>> second = elements.next();
        assertEquals(2, publisher.requested);
        publisher.emit("b");
        assertEquals(Optional.of("b"), join(second));
        CompletionStage<Optional<String>> end = elements.next();
        publisher.complete();
        assertEquals(Optional.empty(), join(end));
        assertEquals(Optional.empty(), join(elements.next()), "the end stays the end");
    }

    @Test
    void oneOperationAtATime() {
        ElementPublisher publisher = new ElementPublisher();
        BodyElements<String> elements = new PublisherBodyElements<>(() -> publisher, () -> { });
        CompletionStage<Optional<String>> first = elements.next();
        assertThrows(IllegalStateException.class, elements::next);
        assertThrows(IllegalStateException.class, () -> elements.forEach(e -> CompletableFuture.completedFuture(null)));
        publisher.emit("a");
        assertEquals(Optional.of("a"), join(first));
        // the next operation can start once the previous one completed
        CompletionStage<Optional<String>> second = elements.next();
        publisher.complete();
        assertEquals(Optional.empty(), join(second));
    }

    @Test
    void completingTheReturnedStageDoesNotLeaveTheOperationInProgress() {
        ElementPublisher publisher = new ElementPublisher();
        BodyElements<String> elements = new PublisherBodyElements<>(() -> publisher, () -> { });
        List<String> consumed = new ArrayList<>();
        CompletionStage<Void> all = elements.forEach(element -> {
            consumed.add(element);
            return CompletableFuture.completedFuture(null);
        });
        // a caller cannot complete or cancel the operation: it ends with the body
        all.toCompletableFuture().complete(null);
        all.toCompletableFuture().cancel(true);
        assertFalse(all.toCompletableFuture().isDone());
        publisher.emit("a");
        publisher.complete();
        join(all);
        assertEquals(List.of("a"), consumed);
        // the operation ended: the next one starts
        assertEquals(Optional.empty(), join(elements.next()));

        ElementPublisher second = new ElementPublisher();
        BodyElements<String> read = new PublisherBodyElements<>(() -> second, () -> { });
        CompletionStage<Optional<String>> next = read.next();
        next.toCompletableFuture().complete(Optional.of("forged"));
        second.emit("b");
        assertEquals(Optional.of("b"), join(next));
    }

    @Test
    void forEachReadsTheNextElementWhenTheConsumerIsDone() {
        ElementPublisher publisher = new ElementPublisher();
        BodyElements<String> elements = new PublisherBodyElements<>(() -> publisher, () -> { });
        List<CompletableFuture<Void>> consumers = new ArrayList<>();
        List<String> consumed = new ArrayList<>();
        CompletionStage<Void> all = elements.forEach(element -> {
            consumed.add(element);
            CompletableFuture<Void> done = new CompletableFuture<>();
            consumers.add(done);
            return done;
        });
        assertEquals(1, publisher.requested);
        publisher.emit("a");
        assertEquals(List.of("a"), consumed);
        assertEquals(1, publisher.requested, "the next element is read when the consumer is done");
        consumers.get(0).complete(null);
        assertEquals(2, publisher.requested);
        publisher.emit("b");
        consumers.get(1).complete(null);
        publisher.complete();
        join(all);
        assertEquals(List.of("a", "b"), consumed);
    }

    @Test
    void forEachConsumesElementsThatAreAvailableAtOnceWithoutRecursion() {
        int count = 100_000;
        List<Integer> source = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            source.add(i);
        }
        BodyElements<Integer> elements = new PublisherBodyElements<>(() -> reactor.core.publisher.Flux.fromIterable(source), () -> { });
        AtomicInteger sum = new AtomicInteger();
        join(elements.forEach(element -> {
            sum.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }));
        assertEquals(count, sum.get());
    }

    @Test
    void aFailingConsumerFailsForEach() {
        ElementPublisher publisher = new ElementPublisher();
        BodyElements<String> elements = new PublisherBodyElements<>(() -> publisher, () -> { });
        CompletionStage<Void> all = elements.forEach(element -> CompletableFuture.failedFuture(new IllegalArgumentException("bad " + element)));
        publisher.emit("a");
        assertEquals("bad a", failure(all).getMessage());
    }

    @Test
    void aDecodingFailureFailsTheRead() {
        ElementPublisher publisher = new ElementPublisher();
        BodyElements<String> elements = new PublisherBodyElements<>(() -> publisher, () -> { });
        CompletionStage<Optional<String>> first = elements.next();
        publisher.fail(new IllegalStateException("malformed"));
        assertEquals("malformed", failure(first).getMessage());
        assertEquals("malformed", failure(elements.next()).getMessage());
    }

    @Test
    void closingBeforeReadingDiscardsTheBody() {
        AtomicBoolean discarded = new AtomicBoolean();
        AtomicBoolean created = new AtomicBoolean();
        BodyElements<String> elements = new PublisherBodyElements<>(() -> {
            created.set(true);
            return new ElementPublisher();
        }, () -> discarded.set(true));
        join(elements.closeAsync());
        assertTrue(discarded.get());
        assertFalse(created.get());
        assertThrows(IllegalStateException.class, elements::next);
        assertEquals(elements.closeAsync(), elements.closeAsync(), "the same stage");
    }

    @Test
    void closingDuringAReadCancelsTheReadAndTheBody() {
        ElementPublisher publisher = new ElementPublisher();
        AtomicBoolean discarded = new AtomicBoolean();
        BodyElements<String> elements = new PublisherBodyElements<>(() -> publisher, () -> discarded.set(true));
        CompletionStage<Void> all = elements.forEach(element -> new CompletableFuture<>());
        publisher.emit("a");
        join(elements.closeAsync());
        assertInstanceOf(CancellationException.class, failure(all));
        assertTrue(publisher.cancelled);
        assertFalse(discarded.get(), "the subscription discards the body");
        assertThrows(IllegalStateException.class, elements::next);
    }

    @Test
    void aSourceThatCannotBeCreatedFailsTheFirstReadAndDiscardsTheBody() {
        AtomicBoolean discarded = new AtomicBoolean();
        BodyElements<String> elements = new PublisherBodyElements<>(() -> {
            throw new UnsupportedOperationException("no reader");
        }, () -> discarded.set(true));
        assertEquals("no reader", failure(elements.next()).getMessage());
        assertTrue(discarded.get());
    }

    @Test
    void theRequestBodyIsReadWithTheLimitOfTheCaller() {
        ElementBytes publisher = new ElementBytes();
        UploadContent content = StreamingUploadContent.requestBody(BODY_FACTORY.adapt(publisher), null, context());
        CompletionStage<byte[]> bytes = content.bytes(5);
        publisher.emit("1234");
        publisher.emit("56");
        Throwable error = failure(bytes);
        assertInstanceOf(ContentLengthExceededException.class, error);
        assertEquals("The content length [6] exceeds the maximum allowed content length [5]", error.getMessage());
        assertTrue(publisher.cancelled);
    }

    @Test
    void theRequestBodyIsWrittenToAFileWithoutALimit() throws Exception {
        ElementBytes publisher = new ElementBytes();
        UploadContent content = StreamingUploadContent.requestBody(BODY_FACTORY.adapt(publisher), null, context());
        Path destination = directory.resolve("body.txt");
        CompletionStage<Void> transfer = content.transferTo(destination);
        String body = "a body larger than the buffer limit of the context: " + "x".repeat(2048);
        publisher.emit(body.substring(0, 100));
        publisher.emit(body.substring(100));
        publisher.complete();
        join(transfer);
        assertEquals(body, Files.readString(destination));
    }

    private static UploadContext context() {
        return new UploadContext(Runnable::run, BODY_FACTORY, StandardCharsets.UTF_8, 16, Long.MAX_VALUE);
    }

    /**
     * Emits the elements the test gives it, and records the demand and the cancellation.
     */
    private static final class ElementPublisher implements Publisher<String> {
        private Subscriber<? super String> subscriber;
        volatile long requested;
        volatile boolean cancelled;

        @Override
        public void subscribe(Subscriber<? super String> s) {
            subscriber = s;
            s.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    requested += n;
                }

                @Override
                public void cancel() {
                    cancelled = true;
                }
            });
        }

        void emit(String value) {
            subscriber.onNext(value);
        }

        void complete() {
            subscriber.onComplete();
        }

        void fail(Throwable t) {
            subscriber.onError(t);
        }
    }

    /**
     * Emits the bytes the test gives it, and records the cancellation.
     */
    private static final class ElementBytes implements Publisher<io.micronaut.core.io.buffer.ReadBuffer> {
        private Subscriber<? super io.micronaut.core.io.buffer.ReadBuffer> subscriber;
        volatile boolean cancelled;

        @Override
        public void subscribe(Subscriber<? super io.micronaut.core.io.buffer.ReadBuffer> s) {
            subscriber = s;
            s.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                }

                @Override
                public void cancel() {
                    cancelled = true;
                }
            });
        }

        void emit(String value) {
            subscriber.onNext(ReadBufferFactory.getJdkFactory().copyOf(value, StandardCharsets.UTF_8));
        }

        void complete() {
            subscriber.onComplete();
        }
    }
}
