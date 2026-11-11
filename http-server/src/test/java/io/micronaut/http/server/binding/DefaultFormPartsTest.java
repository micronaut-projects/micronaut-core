package io.micronaut.http.server.binding;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.form.FormParts;
import io.micronaut.http.multipart.FormFieldMetadata;
import io.micronaut.http.multipart.RawFormField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.stream.Stream;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cursor over the parts of a form, over a request whose raw form fields the test controls.
 */
class DefaultFormPartsTest {

    private static final ByteBodyFactory BODY_FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
    private static final UploadContext CONTEXT = new UploadContext(Runnable::run, BODY_FACTORY, StandardCharsets.UTF_8, 1024, Long.MAX_VALUE);

    private static FormCapableHttpRequest<?> request(Supplier<Publisher<RawFormField>> fields) {
        return (FormCapableHttpRequest<?>) Proxy.newProxyInstance(DefaultFormPartsTest.class.getClassLoader(), new Class<?>[]{FormCapableHttpRequest.class}, (proxy, method, args) -> {
            if (method.getName().equals("getRawFormFields")) {
                return fields.get();
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private static Throwable failure(CompletionStage<?> stage) {
        CompletionException e = assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join());
        return e.getCause();
    }

    private static RawFormField field(String name, String value) {
        return new RawFormField(new FormFieldMetadata(name, null, null), BODY_FACTORY.adapt(ReadBufferFactory.getJdkFactory().copyOf(value, StandardCharsets.UTF_8)));
    }

    @Test
    void aFormThatCannotBeReadFailsEveryOperationWithTheCause() {
        IllegalStateException claimed = new IllegalStateException("claimed by a filter");
        FormParts parts = new DefaultFormParts(request(() -> {
            throw claimed;
        }), CONTEXT);
        CompletionStage<Void> forEach = parts.forEach(part -> CompletableFuture.completedStage(null));
        assertSame(claimed, failure(forEach));
        // not left in progress
        assertSame(claimed, failure(parts.part("title", part -> CompletableFuture.completedStage(null))));
        parts.close();
    }

    @Test
    void completingTheReturnedStageDoesNotEndTheOperation() {
        FieldPublisher publisher = new FieldPublisher();
        FormParts parts = new DefaultFormParts(request(() -> publisher), CONTEXT);
        List<String> visited = new ArrayList<>();
        CompletionStage<Void> forEach = parts.forEach(part -> part.text().thenAccept(visited::add));
        // a caller cannot complete or cancel the operation: it ends with the form
        assertTrue(forEach.toCompletableFuture().complete(null));
        assertTrue(forEach.toCompletableFuture().cancel(true));
        assertFalse(forEach.toCompletableFuture().isDone());
        publisher.emit(field("a", "1"));
        publisher.emit(field("b", "2"));
        publisher.complete();
        forEach.toCompletableFuture().join();
        assertEquals(List.of("1", "2"), visited);
        // the next operation starts
        assertEquals(Boolean.FALSE, parts.part("c", part -> CompletableFuture.completedStage(null)).toCompletableFuture().join());
        parts.close();
    }

    @Test
    void aConsumerThatCompletesLaterGetsTheNextPartThen() {
        FieldPublisher publisher = new FieldPublisher();
        FormParts parts = new DefaultFormParts(() -> publisher, CONTEXT);
        List<String> visited = new ArrayList<>();
        List<CompletableFuture<Void>> stages = new ArrayList<>();
        CompletionStage<Void> forEach = parts.forEach(part -> part.text().thenCompose(text -> {
            visited.add(text);
            CompletableFuture<Void> stage = new CompletableFuture<>();
            stages.add(stage);
            return stage;
        }));
        publisher.emit(field("a", "1"));
        publisher.emit(field("b", "2"));
        assertEquals(List.of("1"), visited);
        stages.get(0).complete(null);
        assertEquals(List.of("1", "2"), visited);
        stages.get(1).complete(null);
        publisher.complete();
        forEach.toCompletableFuture().join();
        parts.close();
    }

    @Test
    void aConsumerThatFailsLaterFailsTheOperation() {
        FieldPublisher publisher = new FieldPublisher();
        FormParts parts = new DefaultFormParts(() -> publisher, CONTEXT);
        CompletableFuture<Void> stage = new CompletableFuture<>();
        CompletionStage<Void> forEach = parts.forEach(part -> stage);
        publisher.emit(field("a", "1"));
        IllegalStateException error = new IllegalStateException("consumer failed");
        stage.completeExceptionally(error);
        assertSame(error, failure(forEach));
        parts.close();
    }

    @Test
    void aConsumerThatThrowsFailsTheOperation() {
        FieldPublisher publisher = new FieldPublisher();
        FormParts parts = new DefaultFormParts(() -> publisher, CONTEXT);
        IllegalStateException error = new IllegalStateException("consumer threw");
        CompletionStage<Boolean> part = parts.part("a", p -> {
            throw error;
        });
        publisher.emit(field("a", "1"));
        assertSame(error, failure(part));
        parts.close();
    }

    @Test
    void overlappingOperationsAndOperationsAfterClosingAreRefused() {
        FieldPublisher publisher = new FieldPublisher();
        FormParts parts = new DefaultFormParts(() -> publisher, CONTEXT);
        CompletionStage<Void> forEach = parts.forEach(part -> CompletableFuture.completedStage(null));
        assertInstanceOf(IllegalStateException.class, failure(parts.part("a", part -> CompletableFuture.completedStage(null))));
        CompletionStage<Void> closed = parts.closeAsync();
        assertSame(closed, parts.closeAsync());
        closed.toCompletableFuture().join();
        assertInstanceOf(CancellationException.class, failure(forEach));
        assertInstanceOf(IllegalStateException.class, failure(parts.forEach(part -> CompletableFuture.completedStage(null))));
    }

    @Test
    void closingReleasesThePartBeingRead() {
        FieldPublisher publisher = new FieldPublisher();
        FormParts parts = new DefaultFormParts(() -> publisher, CONTEXT);
        CompletableFuture<Void> stage = new CompletableFuture<>();
        CompletionStage<Void> forEach = parts.forEach(part -> stage);
        publisher.emit(field("a", "1"));
        parts.closeAsync().toCompletableFuture().join();
        assertInstanceOf(CancellationException.class, failure(forEach));
        stage.complete(null);
    }

    @Test
    void closingWaitsForTheReleaseOfAPartWhoseConsumerCompleted(@TempDir Path directory) throws IOException {
        Queue<Runnable> tasks = new ArrayDeque<>();
        UploadContext context = new UploadContext(tasks::add, BODY_FACTORY, StandardCharsets.UTF_8, 1024, Long.MAX_VALUE);
        FieldPublisher publisher = new FieldPublisher();
        FormParts parts = new DefaultFormParts(() -> publisher, context);
        // a file that is still arriving
        RawFormField file = new RawFormField(new FormFieldMetadata("file", "file.txt", null), BODY_FACTORY.adapt(Flux.<ReadBuffer>never()));
        CompletionStage<Void> forEach = parts.forEach(part -> {
            // the transfer is left running: the part is released when the consumer completed
            part.file().transferTo(directory.resolve("file.txt"));
            runAll(tasks);
            return CompletableFuture.completedStage(null);
        });
        publisher.emit(file);
        assertEquals(1, stagingFiles(directory).size(), "the staging file of the transfer");

        // the aborted transfer deletes its staging file on the I/O executor
        CompletionStage<Void> closed = parts.closeAsync();
        assertFalse(closed.toCompletableFuture().isDone(), "closing waits for the release of the part");
        runAll(tasks);
        closed.toCompletableFuture().join();
        assertEquals(List.of(), stagingFiles(directory));
        assertInstanceOf(CancellationException.class, failure(forEach));
    }

    private static void runAll(Queue<Runnable> tasks) {
        Runnable task;
        while ((task = tasks.poll()) != null) {
            task.run();
        }
    }

    private static List<Path> stagingFiles(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(p -> p.getFileName().toString().startsWith(".upload-")).toList();
        }
    }

    /**
     * Emits the fields the test gives it, one per request.
     */
    private static final class FieldPublisher implements Publisher<RawFormField> {
        private Subscriber<? super RawFormField> subscriber;
        private final List<RawFormField> queued = new ArrayList<>();
        private long requested;
        private boolean completed;

        @Override
        public void subscribe(Subscriber<? super RawFormField> s) {
            subscriber = s;
            s.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    requested += n;
                    drain();
                }

                @Override
                public void cancel() {
                    // the test does not check the cancellation
                }
            });
        }

        void emit(RawFormField field) {
            queued.add(field);
            drain();
        }

        void complete() {
            completed = true;
            drain();
        }

        private void drain() {
            if (subscriber == null) {
                return;
            }
            while (requested > 0 && !queued.isEmpty()) {
                requested--;
                subscriber.onNext(queued.remove(0));
            }
            if (completed && queued.isEmpty()) {
                completed = false;
                subscriber.onComplete();
            }
        }
    }
}
