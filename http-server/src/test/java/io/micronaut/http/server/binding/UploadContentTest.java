package io.micronaut.http.server.binding;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.io.file.TemporaryFileResource;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.form.FileUpload;
import io.micronaut.http.form.FormFieldException;
import io.micronaut.http.form.FormPart;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.FormFieldMetadata;
import io.micronaut.http.multipart.RawFormField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lifecycle of the content of form fields, the same for every storage: in memory, on disk and
 * still arriving. Disk work runs on a queue that the tests run explicitly, so the races between
 * closing and running operations are deterministic.
 */
class UploadContentTest {

    private static final ByteBodyFactory BODY_FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
    private static final FormFieldMetadata FILE = new FormFieldMetadata("file", "file.txt", MediaType.TEXT_PLAIN_TYPE);
    private static final FormFieldMetadata TEXT = new FormFieldMetadata("title", null, null);

    @TempDir
    Path directory;

    private final ManualExecutor executor = new ManualExecutor();

    private UploadContext context(long maxFileSize) {
        return context(executor, maxFileSize);
    }

    private static UploadContext context(Executor ioExecutor, long maxFileSize) {
        return new UploadContext(ioExecutor, BODY_FACTORY, StandardCharsets.UTF_8, 1024, maxFileSize);
    }

    private FileUpload memory(String content) {
        ReadBuffer buffer = ReadBufferFactory.getJdkFactory().copyOf(content, StandardCharsets.UTF_8);
        return new DefaultFileUpload(new StoredUploadContent(CompletedFileUpload.ofMemory(FILE, buffer), context(Long.MAX_VALUE)));
    }

    private Path temporary(String content) throws IOException {
        Path file = Files.createTempFile(directory, "stored", ".tmp");
        Files.writeString(file, content);
        return file;
    }

    private FileUpload disk(Path temporary) throws IOException {
        CompletedFileUpload upload = CompletedFileUpload.ofFile(FILE, new TemporaryFileResource(temporary), Files.size(temporary));
        return new DefaultFileUpload(new StoredUploadContent(upload, context(Long.MAX_VALUE)));
    }

    private static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static Throwable failure(CompletionStage<?> stage) {
        CompletionException e = assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join());
        return e.getCause();
    }

    private List<Path> temporaryFiles() throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(p -> p.getFileName().toString().startsWith("stored")).toList();
        }
    }

    private List<Path> leftovers() throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(p -> p.getFileName().toString().startsWith(".upload-")).toList();
        }
    }

    @Test
    void memoryUploadIsReadOnce() {
        FileUpload upload = memory("content");
        assertEquals(OptionalLong.of(7), upload.size());
        CompletionStage<byte[]> bytes = upload.bytes(100);
        executor.runAll();
        assertArrayEquals("content".getBytes(StandardCharsets.UTF_8), join(bytes));
        assertThrows(IllegalStateException.class, () -> upload.bytes(100));
        assertThrows(IllegalStateException.class, () -> upload.transferTo(directory.resolve("copy")));
        assertThrows(IllegalStateException.class, upload::takeBody);
        // the metadata stays readable
        assertEquals("file.txt", upload.fileName());
        assertEquals(OptionalLong.of(7), upload.size());
        join(upload.closeAsync());
    }

    @Test
    void limitsAreCheckedBeforeClaimingAndWhileReading() {
        FileUpload upload = memory("content");
        assertThrows(IllegalArgumentException.class, () -> upload.bytes(-1));
        // a rejected call does not consume the upload
        CompletionStage<byte[]> tooLarge = upload.bytes(6);
        executor.runAll();
        assertInstanceOf(ContentLengthExceededException.class, failure(tooLarge));
        // a failed read consumed it
        assertThrows(IllegalStateException.class, () -> upload.bytes(7));

        FileUpload exact = memory("content");
        CompletionStage<byte[]> bytes = exact.bytes(7);
        executor.runAll();
        assertEquals(7, join(bytes).length);

        FileUpload empty = memory("");
        CompletionStage<byte[]> none = empty.bytes(0);
        executor.runAll();
        assertEquals(0, join(none).length);
    }

    @Test
    void transferCreatesANewFileAndNeverReplacesOne() throws IOException {
        FileUpload upload = memory("content");
        Path destination = directory.resolve("new.txt");
        CompletionStage<Void> transfer = upload.transferTo(destination);
        executor.runAll();
        join(transfer);
        assertEquals("content", Files.readString(destination));
        // closing afterwards does not touch the file of the application
        join(upload.closeAsync());
        assertEquals("content", Files.readString(destination));

        Path existing = Files.writeString(directory.resolve("existing.txt"), "keep");
        FileUpload second = memory("other");
        CompletionStage<Void> refused = second.transferTo(existing);
        executor.runAll();
        assertInstanceOf(FileAlreadyExistsException.class, failure(refused));
        assertEquals("keep", Files.readString(existing));

        FileUpload third = memory("other");
        CompletionStage<Void> noDirectory = third.transferTo(directory.resolve("missing").resolve("file.txt"));
        executor.runAll();
        assertInstanceOf(NoSuchFileException.class, failure(noDirectory));
        assertEquals(List.of(), leftovers());
    }

    @Test
    void diskUploadIsMovedAndItsTemporaryFileDeleted() throws IOException {
        Path temporary = temporary("on disk");
        FileUpload upload = disk(temporary);
        Path destination = directory.resolve("moved.txt");
        CompletionStage<Void> transfer = upload.transferTo(destination);
        assertFalse(transfer.toCompletableFuture().isDone(), "disk work runs on the executor");
        executor.runAll();
        join(transfer);
        assertEquals("on disk", Files.readString(destination));
        assertFalse(Files.exists(temporary));
        CompletionStage<Void> closed = upload.closeAsync();
        executor.runAll();
        join(closed);
        assertTrue(Files.exists(destination));
        assertEquals(List.of(), leftovers());
    }

    @Test
    void closingReleasesTheContentOnceAndReturnsTheSameStage() throws IOException {
        Path temporary = temporary("on disk");
        FileUpload upload = disk(temporary);
        CompletionStage<Void> closed = upload.closeAsync();
        assertSame(closed, upload.closeAsync());
        upload.close();
        assertFalse(closed.toCompletableFuture().isDone(), "the file is deleted on the executor");
        executor.runAll();
        join(closed);
        assertFalse(Files.exists(temporary));
        assertThrows(IllegalStateException.class, () -> upload.bytes(10));
        assertEquals("file.txt", upload.fileName());
    }

    @Test
    void closingAbortsAQueuedOperation() throws IOException {
        Path temporary = temporary("on disk");
        FileUpload upload = disk(temporary);
        Path destination = directory.resolve("aborted.txt");
        CompletionStage<Void> transfer = upload.transferTo(destination);
        CompletionStage<Void> closed = upload.closeAsync();
        assertFalse(closed.toCompletableFuture().isDone(), "closing waits for the operation to stop");
        executor.runAll();
        join(closed);
        assertInstanceOf(CancellationException.class, failure(transfer));
        assertFalse(Files.exists(destination));
        assertFalse(Files.exists(temporary));
        assertEquals(List.of(), leftovers());
    }

    @Test
    void takenBodyOwnsTheFile() throws IOException {
        Path temporary = temporary("taken");
        FileUpload upload = disk(temporary);
        CloseableByteBody body = upload.takeBody();
        // nothing is read yet, and the upload no longer owns the file
        assertTrue(Files.exists(temporary));
        CompletionStage<Void> closed = upload.closeAsync();
        executor.runAll();
        join(closed);
        assertTrue(Files.exists(temporary));
        assertThrows(IllegalStateException.class, () -> upload.bytes(10));

        CompletableFuture<? extends CloseableAvailableByteBody> buffered = body.buffer();
        executor.runAll();
        try (CloseableAvailableByteBody available = buffered.join()) {
            assertEquals("taken", available.toString(StandardCharsets.UTF_8));
        }
        executor.runAll();
        assertFalse(Files.exists(temporary), "consuming the body deletes the file");
    }

    @Test
    void takenMemoryBodyIsIndependentOfTheUpload() {
        FileUpload upload = memory("taken");
        CloseableByteBody body = upload.takeBody();
        upload.close();
        try (CloseableAvailableByteBody available = body.buffer().join()) {
            assertEquals("taken", available.toString(StandardCharsets.UTF_8));
        }
    }

    @Test
    void streamingReadCollectsTheArrivingContent() {
        TestPublisher publisher = new TestPublisher();
        FormPart part = new DefaultFormPart(new StreamingUploadContent(new RawFormField(TEXT, BODY_FACTORY.adapt(publisher)), context(Long.MAX_VALUE)));
        CompletionStage<String> text = part.text(10);
        publisher.emit("Hello ");
        publisher.emit("form");
        assertFalse(text.toCompletableFuture().isDone());
        publisher.complete();
        assertEquals("Hello form", join(text));
    }

    @Test
    void streamingReadOverTheLimitCancelsTheUpstream() {
        TestPublisher publisher = new TestPublisher();
        FormPart part = new DefaultFormPart(new StreamingUploadContent(new RawFormField(TEXT, BODY_FACTORY.adapt(publisher)), context(Long.MAX_VALUE)));
        CompletionStage<byte[]> bytes = part.bytes(5);
        publisher.emit("1234");
        publisher.emit("56");
        assertInstanceOf(ContentLengthExceededException.class, failure(bytes));
        assertTrue(publisher.cancelled);
    }

    @Test
    void streamingFileLimitAppliesWhateverTheCallerAsks() {
        TestPublisher publisher = new TestPublisher();
        FormPart part = new DefaultFormPart(new StreamingUploadContent(new RawFormField(FILE, BODY_FACTORY.adapt(publisher)), context(3)));
        CompletionStage<byte[]> bytes = part.file().bytes(100);
        publisher.emit("1234");
        assertInstanceOf(ContentLengthExceededException.class, failure(bytes));
    }

    @Test
    void streamingTransferWritesOnTheExecutorAndPublishes() throws IOException {
        TestPublisher publisher = new TestPublisher();
        FormPart part = new DefaultFormPart(new StreamingUploadContent(new RawFormField(FILE, BODY_FACTORY.adapt(publisher)), context(Long.MAX_VALUE)));
        FileUpload file = part.file();
        assertSame(file, part.file(), "the same view on every call");
        Path destination = directory.resolve("streamed.txt");
        CompletionStage<Void> transfer = file.transferTo(destination);
        executor.runAll();
        publisher.emit("first ");
        executor.runAll();
        publisher.emit("second");
        executor.runAll();
        assertEquals(OptionalLong.empty(), file.size(), "the size is known once all of it arrived");
        publisher.complete();
        executor.runAll();
        join(transfer);
        assertEquals("first second", Files.readString(destination));
        assertEquals(OptionalLong.of(12), file.size());
        // the part and its file share the content
        assertThrows(IllegalStateException.class, part::text);
        assertEquals(List.of(), leftovers());
    }

    @Test
    void aWriteFailingAfterTheLastBufferFailsTheTransferAndPublishesNothing() throws IOException {
        TestPublisher publisher = new TestPublisher();
        FormPart part = new DefaultFormPart(new StreamingUploadContent(new RawFormField(FILE, BODY_FACTORY.adapt(publisher)), context(Long.MAX_VALUE)));
        Path destination = directory.resolve("corrupted.txt");
        CompletionStage<Void> transfer = part.file().transferTo(destination);
        executor.runAll();
        // a buffer that cannot be written, then the end of the upload, before the write runs
        publisher.emit(new UnwritableBuffer());
        publisher.complete();
        executor.runAll();
        assertInstanceOf(RuntimeException.class, failure(transfer));
        assertFalse(Files.exists(destination), "a failed transfer publishes nothing");
        assertEquals(OptionalLong.empty(), part.file().size());
        assertEquals(List.of(), leftovers());
        join(part.closeAsync());
    }

    @Test
    void aRejectingExecutorFailsTheTransferAndClosingCompletes() throws IOException {
        TestPublisher publisher = new TestPublisher();
        Executor rejecting = command -> {
            throw new RejectedExecutionException("no I/O thread");
        };
        FormPart part = new DefaultFormPart(new StreamingUploadContent(new RawFormField(FILE, BODY_FACTORY.adapt(publisher)), context(rejecting, Long.MAX_VALUE)));
        Path destination = directory.resolve("rejected.txt");
        CompletionStage<Void> transfer = part.file().transferTo(destination);
        assertInstanceOf(RejectedExecutionException.class, failure(transfer));
        assertTrue(publisher.cancelled);
        join(part.closeAsync());
        assertFalse(Files.exists(destination));
        assertEquals(List.of(), leftovers());
    }

    @Test
    void anExecutorRejectingDuringTheTransferReleasesTheStagingFile() throws IOException {
        TestPublisher publisher = new TestPublisher();
        ManualExecutor limited = new ManualExecutor();
        FormPart part = new DefaultFormPart(new StreamingUploadContent(new RawFormField(FILE, BODY_FACTORY.adapt(publisher)), context(limited, Long.MAX_VALUE)));
        Path destination = directory.resolve("half.txt");
        CompletionStage<Void> transfer = part.file().transferTo(destination);
        // the staging file is open, then the executor shuts down
        limited.runAll();
        limited.reject = true;
        publisher.emit("first");
        assertInstanceOf(RejectedExecutionException.class, failure(transfer));
        assertTrue(publisher.cancelled);
        join(part.closeAsync());
        assertFalse(Files.exists(destination));
        assertEquals(List.of(), leftovers(), "the staging file is deleted on the rejecting thread");
    }

    @Test
    void aStagingFailureBeforeTheSubscriptionFailsWithItsCause() throws IOException {
        // the disk thread fails to create the staging file before the caller subscribed: the
        // caller still claims the body, and the upstream is cancelled when it subscribes
        TestPublisher publisher = new TestPublisher();
        ManualExecutor racing = new ManualExecutor();
        racing.runInline = 1;
        FormPart part = new DefaultFormPart(new StreamingUploadContent(new RawFormField(FILE, BODY_FACTORY.adapt(publisher)), context(racing, Long.MAX_VALUE)));
        Path existing = Files.writeString(directory.resolve("existing.txt"), "keep");
        CompletionStage<Void> transfer = part.file().transferTo(existing);
        assertTrue(publisher.cancelled, "the subscription is cancelled once it arrives");
        racing.runAll();
        assertInstanceOf(FileAlreadyExistsException.class, failure(transfer));
        assertEquals("keep", Files.readString(existing));
        join(part.closeAsync());
        assertEquals(List.of(), leftovers());

        TestPublisher second = new TestPublisher();
        racing.runInline = 1;
        FormPart missing = new DefaultFormPart(new StreamingUploadContent(new RawFormField(FILE, BODY_FACTORY.adapt(second)), context(racing, Long.MAX_VALUE)));
        CompletionStage<Void> noDirectory = missing.file().transferTo(directory.resolve("missing").resolve("file.txt"));
        racing.runAll();
        assertInstanceOf(NoSuchFileException.class, failure(noDirectory));
        assertTrue(second.cancelled);
        assertEquals(List.of(), leftovers());
    }

    @Test
    void closingThePartAbortsAStreamingTransfer() throws IOException {
        TestPublisher publisher = new TestPublisher();
        FormPart part = new DefaultFormPart(new StreamingUploadContent(new RawFormField(FILE, BODY_FACTORY.adapt(publisher)), context(Long.MAX_VALUE)));
        Path destination = directory.resolve("aborted.txt");
        CompletionStage<Void> transfer = part.file().transferTo(destination);
        executor.runAll();
        publisher.emit("partial");
        // closing the view closes the part
        CompletionStage<Void> closed = part.file().closeAsync();
        assertSame(closed, part.closeAsync());
        executor.runAll();
        join(closed);
        assertInstanceOf(CancellationException.class, failure(transfer));
        assertTrue(publisher.cancelled);
        assertFalse(Files.exists(destination));
        assertEquals(List.of(), leftovers());
    }

    @Test
    void aTextPartIsNotAFile() {
        TestPublisher publisher = new TestPublisher();
        FormPart part = new DefaultFormPart(new StreamingUploadContent(new RawFormField(TEXT, BODY_FACTORY.adapt(publisher)), context(Long.MAX_VALUE)));
        assertFalse(part.isFile());
        FormFieldException e = assertThrows(FormFieldException.class, part::file);
        assertEquals("title", e.getFieldName());
        // asking did not consume it
        CompletionStage<String> text = part.text();
        publisher.emit("value");
        publisher.complete();
        assertEquals("value", join(text));
    }

    @Test
    void closingAnUnreadStreamingPartDiscardsIt() {
        TestPublisher publisher = new TestPublisher();
        FormPart part = new DefaultFormPart(new StreamingUploadContent(new RawFormField(FILE, BODY_FACTORY.adapt(publisher)), context(Long.MAX_VALUE)));
        join(part.closeAsync());
        assertThrows(IllegalStateException.class, () -> part.file().bytes(10));
    }

    @Test
    void storedUploadIsWrittenToAStreamOnTheExecutorWithoutClosingIt() throws IOException {
        FileUpload upload = disk(temporary("stored content"));
        TrackingOutputStream out = new TrackingOutputStream();
        CompletionStage<Void> transfer = upload.transferTo(out);
        assertEquals(0, out.size(), "written on the I/O executor");
        executor.runAll();
        join(transfer);
        assertEquals("stored content", out.toString(StandardCharsets.UTF_8));
        assertFalse(out.closed);
        assertTrue(out.flushed);
        assertThrows(IllegalStateException.class, () -> upload.transferTo(new ByteArrayOutputStream()));
        assertThrows(IllegalStateException.class, upload::readAllBytes);
        assertEquals(List.of(), temporaryFiles(), "the temporary file of the upload was released");
    }

    @Test
    void streamingContentIsWrittenToAStreamAsItArrives() {
        TestPublisher publisher = new TestPublisher();
        FormPart part = new DefaultFormPart(new StreamingUploadContent(new RawFormField(FILE, BODY_FACTORY.adapt(publisher)), context(Long.MAX_VALUE)));
        TrackingOutputStream out = new TrackingOutputStream();
        CompletionStage<Void> transfer = part.transferTo(out);
        publisher.emit("first ");
        assertEquals("first ", out.toString(StandardCharsets.UTF_8), "written by the thread that delivers the content");
        publisher.emit("second");
        assertFalse(transfer.toCompletableFuture().isDone());
        publisher.complete();
        join(transfer);
        assertEquals("first second", out.toString(StandardCharsets.UTF_8));
        assertFalse(out.closed);
        assertTrue(out.flushed);
        assertEquals(OptionalLong.of(12), part.file().size());
        assertThrows(IllegalStateException.class, () -> part.transferTo(new ByteArrayOutputStream()));
    }

    @Test
    void streamingContentOverTheFileLimitIsNotWrittenToAStream() {
        TestPublisher publisher = new TestPublisher();
        FormPart part = new DefaultFormPart(new StreamingUploadContent(new RawFormField(FILE, BODY_FACTORY.adapt(publisher)), context(5)));
        TrackingOutputStream out = new TrackingOutputStream();
        CompletionStage<Void> transfer = part.file().transferTo(out);
        publisher.emit("1234");
        publisher.emit("56");
        assertInstanceOf(ContentLengthExceededException.class, failure(transfer));
        assertTrue(publisher.cancelled);
        assertEquals("1234", out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void closingThePartAbortsAStreamTransfer() {
        TestPublisher publisher = new TestPublisher();
        FormPart part = new DefaultFormPart(new StreamingUploadContent(new RawFormField(FILE, BODY_FACTORY.adapt(publisher)), context(Long.MAX_VALUE)));
        CompletionStage<Void> transfer = part.transferTo(new ByteArrayOutputStream());
        publisher.emit("partial");
        join(part.closeAsync());
        assertInstanceOf(CancellationException.class, failure(transfer));
        assertTrue(publisher.cancelled);
    }

    @Test
    void aStreamThatFailsFailsTheTransfer() {
        TestPublisher publisher = new TestPublisher();
        FormPart part = new DefaultFormPart(new StreamingUploadContent(new RawFormField(FILE, BODY_FACTORY.adapt(publisher)), context(Long.MAX_VALUE)));
        CompletionStage<Void> transfer = part.transferTo(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("write failed");
            }
        });
        publisher.emit("content");
        assertEquals("write failed", failure(transfer).getMessage());
        assertTrue(publisher.cancelled);
    }

    @Test
    void completeUploadsAreReadBlocking() throws IOException {
        FileUpload memory = memory("in memory");
        assertEquals("in memory", memory.readString());
        assertThrows(IllegalStateException.class, memory::readAllBytes);
        assertThrows(IllegalStateException.class, () -> memory.bytes(100));

        FileUpload disk = disk(temporary("on disk"));
        assertArrayEquals("on disk".getBytes(StandardCharsets.UTF_8), disk.readAllBytes());
        assertEquals(List.of(), temporaryFiles(), "the temporary file of the upload was released");
        assertThrows(IllegalStateException.class, disk::readString);
        // the metadata stays readable
        assertEquals("file.txt", disk.fileName());
        join(disk.closeAsync());

        FileUpload closed = memory("closed");
        join(closed.closeAsync());
        assertThrows(IllegalStateException.class, closed::readAllBytes);
    }

    @Test
    void aDiskUploadIsNotReadBlockingOnAnIoThread() throws Exception {
        Path temporary = temporary("on disk");
        FileUpload disk = disk(temporary);
        FileUpload memory = memory("in memory");
        Scheduler nonBlocking = Schedulers.newSingle("non-blocking");
        try {
            CompletableFuture<Throwable> refused = CompletableFuture.supplyAsync(() -> {
                try {
                    disk.readString();
                    return null;
                } catch (Throwable e) {
                    return e;
                }
            }, nonBlocking::schedule);
            IllegalStateException e = assertInstanceOf(IllegalStateException.class, refused.get());
            assertTrue(e.getMessage().contains("bytes(int)"), e.getMessage());
            assertTrue(e.getSuppressed().length == 0, "one clear failure");
            // content in memory does not block
            CompletableFuture<String> read = CompletableFuture.supplyAsync(memory::readString, nonBlocking::schedule);
            assertEquals("in memory", read.get());
        } finally {
            nonBlocking.dispose();
        }
        // the refused read did not claim the upload, nor release its file
        assertTrue(Files.exists(temporary));
        CompletionStage<byte[]> bytes = disk.bytes(100);
        executor.runAll();
        assertEquals("on disk", new String(join(bytes), StandardCharsets.UTF_8));
        assertEquals(List.of(), temporaryFiles(), "the temporary file of the upload was released");
    }

    @Test
    void aFailedBlockingReadReleasesTheUploadAndKeepsItsCause() throws IOException {
        Path temporary = temporary("on disk");
        FileUpload disk = disk(temporary);
        // the file disappears: the read fails, and the failure of the release does not hide it
        Files.delete(temporary);
        UncheckedIOException e = assertThrows(UncheckedIOException.class, disk::readAllBytes);
        assertInstanceOf(NoSuchFileException.class, e.getCause());
        assertThrows(IllegalStateException.class, disk::readAllBytes);
        join(disk.closeAsync());
    }

    @Test
    void anUploadThatIsStillArrivingIsNotReadBlocking() {
        TestPublisher publisher = new TestPublisher();
        FormPart part = new DefaultFormPart(new StreamingUploadContent(new RawFormField(FILE, BODY_FACTORY.adapt(publisher)), context(Long.MAX_VALUE)));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> part.file().readAllBytes());
        assertTrue(e.getMessage().contains("still arriving"), e.getMessage());
        assertThrows(IllegalStateException.class, () -> part.file().readString());
        // not consumed: it can still be read asynchronously
        CompletionStage<byte[]> bytes = part.file().bytes(100);
        publisher.emit("arrived");
        publisher.complete();
        assertEquals("arrived", new String(join(bytes), StandardCharsets.UTF_8));
    }

    /**
     * Records whether it was flushed and closed.
     */
    private static final class TrackingOutputStream extends ByteArrayOutputStream {
        volatile boolean flushed;
        volatile boolean closed;

        @Override
        public void flush() {
            flushed = true;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /**
     * Runs the tasks it is given when the test says so.
     */
    private static final class ManualExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        volatile boolean reject;
        // the next tasks that run at once, like on another thread that is faster than the caller
        volatile int runInline;

        @Override
        public void execute(Runnable command) {
            synchronized (this) {
                if (reject) {
                    throw new RejectedExecutionException("shut down");
                }
                if (runInline <= 0) {
                    tasks.add(command);
                    return;
                }
                runInline--;
            }
            command.run();
        }

        void runAll() {
            while (true) {
                Runnable task;
                synchronized (this) {
                    task = tasks.poll();
                }
                if (task == null) {
                    return;
                }
                task.run();
            }
        }
    }

    /**
     * A buffer whose size is known but whose content cannot be read, like a failing disk write.
     */
    private static final class UnwritableBuffer extends ReadBuffer {
        private int size;
        private boolean closed;

        UnwritableBuffer() {
            this(4);
        }

        private UnwritableBuffer(int size) {
            this.size = size;
        }

        @Override
        public int readable() {
            return size;
        }

        @Override
        public ReadBuffer duplicate() {
            return new UnwritableBuffer(size);
        }

        @Override
        public ReadBuffer split(int splitPosition) {
            UnwritableBuffer first = new UnwritableBuffer(splitPosition);
            size -= splitPosition;
            return first;
        }

        @Override
        public ReadBuffer move() {
            UnwritableBuffer moved = new UnwritableBuffer(size);
            closed = true;
            return moved;
        }

        @Override
        public void toArray(byte[] destination, int offset) {
            closed = true;
            throw new UncheckedIOException(new IOException("write failed"));
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        protected boolean isConsumed() {
            return closed;
        }

        @Override
        protected byte[] peekArray(int n) {
            throw new UncheckedIOException(new IOException("write failed"));
        }
    }

    /**
     * Emits the buffers the test gives it, and records the cancellation.
     */
    private static final class TestPublisher implements Publisher<ReadBuffer> {
        private Subscriber<? super ReadBuffer> subscriber;
        volatile boolean cancelled;

        @Override
        public void subscribe(Subscriber<? super ReadBuffer> s) {
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
            emit(ReadBufferFactory.getJdkFactory().copyOf(value, StandardCharsets.UTF_8));
        }

        void emit(ReadBuffer buffer) {
            subscriber.onNext(buffer);
        }

        void complete() {
            subscriber.onComplete();
        }
    }
}
