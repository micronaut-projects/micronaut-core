package io.micronaut.http.body;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link ByteBodyFactory#streaming(java.util.concurrent.Executor, io.micronaut.core.util.functional.ThrowingConsumer)}:
 * the bytes a writer writes to an output stream are streamed with backpressure.
 */
@Timeout(10)
class StreamingOutputByteBodyTest {
    private static final ByteBodyFactory FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    @AfterAll
    static void shutdown() {
        EXECUTOR.shutdownNow();
    }

    @Test
    void streamsWhatTheWriterWrites() throws Exception {
        byte[] expected = new byte[300_000];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) i;
        }
        try (CloseableByteBody body = FACTORY.streaming(EXECUTOR, out -> {
            // single bytes, small and large writes
            out.write(expected[0]);
            out.write(expected, 1, 99);
            out.write(expected, 100, expected.length - 100);
        })) {
            Assertions.assertTrue(body.expectedLength().isEmpty());
            Assertions.assertArrayEquals(expected, body.toInputStream().readAllBytes());
        }
    }

    @Test
    void anEmptyWriterGivesAnEmptyBody() throws Exception {
        try (CloseableByteBody body = FACTORY.streaming(EXECUTOR, out -> { })) {
            Assertions.assertEquals(0, body.toInputStream().readAllBytes().length);
        }
    }

    @Test
    void flushPassesOnWhatWasWritten() throws Exception {
        CompletableFuture<Void> release = new CompletableFuture<>();
        try (CloseableByteBody body = FACTORY.streaming(EXECUTOR, out -> {
            out.write("hello".getBytes(StandardCharsets.UTF_8));
            out.flush();
            release.get();
        }); InputStream in = body.toInputStream()) {
            byte[] read = new byte[100];
            Assertions.assertEquals("hello", new String(read, 0, in.read(read), StandardCharsets.UTF_8));
            release.complete(null);
            Assertions.assertEquals(-1, in.read());
        }
    }

    @Test
    void theWriterWaitsForTheReader() throws Exception {
        AtomicLong written = new AtomicLong();
        byte[] piece = new byte[1024];
        try (CloseableByteBody body = FACTORY.streaming(EXECUTOR, out -> {
            for (int i = 0; i < 10_000; i++) {
                out.write(piece);
                written.addAndGet(piece.length);
            }
        }); InputStream in = body.toInputStream()) {
            Assertions.assertEquals(1, in.read(new byte[1]));
            Thread.sleep(300);
            // 10 MiB are not written ahead of a reader that read one byte
            Assertions.assertTrue(written.get() < 1024 * 1024, "written: " + written.get());
            long rest = in.transferTo(java.io.OutputStream.nullOutputStream());
            Assertions.assertEquals(10_000L * 1024 - 1, rest);
        }
    }

    @Test
    void theExceptionOfTheWriterFailsTheBody() {
        IOException failure = new IOException("writer failed");
        try (CloseableByteBody body = FACTORY.streaming(EXECUTOR, out -> {
            out.write(1);
            throw failure;
        })) {
            ExecutionException e = Assertions.assertThrows(ExecutionException.class, () -> body.buffer().get(5, TimeUnit.SECONDS));
            Assertions.assertSame(failure, e.getCause());
        }
    }

    @Test
    void theWriterIsNotCalledForABodyThatIsNotRead() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        FACTORY.streaming(EXECUTOR, out -> called.set(true)).close();
        Thread.sleep(100);
        Assertions.assertFalse(called.get());
    }

    @Test
    void discardingTheBodyStopsTheWriter() throws Exception {
        CompletableFuture<Throwable> writerFailure = new CompletableFuture<>();
        byte[] piece = new byte[1024];
        CloseableByteBody body = FACTORY.streaming(EXECUTOR, out -> {
            try {
                while (true) {
                    out.write(piece);
                }
            } catch (IOException e) {
                writerFailure.complete(e);
                throw e;
            }
        });
        InputStream in = body.toInputStream();
        Assertions.assertEquals(1, in.read(new byte[1]));
        in.close();
        body.close();
        Assertions.assertInstanceOf(ByteBody.BodyDiscardedException.class, writerFailure.get(5, TimeUnit.SECONDS));
    }
}
