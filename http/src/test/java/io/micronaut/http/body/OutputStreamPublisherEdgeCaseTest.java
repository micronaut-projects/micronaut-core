package io.micronaut.http.body;

import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.util.functional.ThrowingConsumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Edge cases of {@link OutputStreamPublisher}: protocol violations, executor rejection,
 * stream misuse and interruption of a blocked writer.
 */
@Timeout(10)
class OutputStreamPublisherEdgeCaseTest {

    private static final java.util.concurrent.Executor NEW_THREAD = r -> new Thread(r).start();

    private static OutputStreamPublisher publisher(java.util.concurrent.Executor executor, ThrowingConsumer<? super OutputStream, ?> writer) {
        return new OutputStreamPublisher(executor, writer, ReadBufferFactory.getJdkFactory(), () -> false);
    }

    @Test
    void aSecondSubscriberIsRejected() {
        OutputStreamPublisher p = publisher(NEW_THREAD, out -> { });
        Recorder first = new Recorder(Long.MAX_VALUE);
        p.subscribe(first);
        first.done.join();
        Assertions.assertThrows(IllegalStateException.class, () -> p.subscribe(new Recorder(1)));
    }

    @Test
    void aRejectingExecutorFailsTheSubscriber() {
        OutputStreamPublisher p = publisher(r -> {
            throw new RejectedExecutionException("rejected");
        }, out -> Assertions.fail("must not run"));
        Recorder r = new Recorder(0);
        p.subscribe(r);
        Throwable t = Assertions.assertThrows(Exception.class, r.done::join).getCause();
        Assertions.assertInstanceOf(RejectedExecutionException.class, t);
    }

    @Test
    void aNonPositiveRequestFailsTheSubscriberAndStopsTheWriter() throws Exception {
        AtomicBoolean writerEntered = new AtomicBoolean();
        CompletableFuture<Runnable> task = new CompletableFuture<>();
        OutputStreamPublisher p = publisher(task::complete, out -> writerEntered.set(true));
        Recorder r = new Recorder(0);
        p.subscribe(r);
        r.subscription.request(0);
        Throwable t = Assertions.assertThrows(Exception.class, r.done::join).getCause();
        Assertions.assertInstanceOf(IllegalArgumentException.class, t);
        // a second violation is not signalled again
        r.subscription.request(-1);
        task.get().run();
        Assertions.assertFalse(writerEntered.get());
        Assertions.assertEquals(1, r.errors.size());
        Assertions.assertTrue(r.items.isEmpty());
    }

    @Test
    void aNonPositiveRequestStopsAWriterBlockedInAWrite() throws Exception {
        CompletableFuture<Thread> writerThread = new CompletableFuture<>();
        CompletableFuture<Throwable> writerFailure = new CompletableFuture<>();
        OutputStreamPublisher p = publisher(NEW_THREAD, out -> {
            writerThread.complete(Thread.currentThread());
            try {
                out.write(new byte[OutputStreamPublisher.CHUNK_SIZE]); // blocks, no demand
            } catch (Throwable e) {
                writerFailure.complete(e);
                throw e;
            }
        });
        Recorder r = new Recorder(0);
        p.subscribe(r);
        Thread thread = writerThread.get(5, TimeUnit.SECONDS);
        while (thread.getState() != Thread.State.WAITING) {
            Thread.onSpinWait();
        }
        r.subscription.request(0);
        Assertions.assertInstanceOf(ByteBody.BodyDiscardedException.class, writerFailure.get(5, TimeUnit.SECONDS));
        Throwable t = Assertions.assertThrows(Exception.class, r.done::join).getCause();
        Assertions.assertInstanceOf(IllegalArgumentException.class, t);
        thread.join(5000);
        Assertions.assertEquals(1, r.errors.size());
        Assertions.assertTrue(r.items.isEmpty());
    }

    @Test
    void aWriterOnTheSubscribingThreadFails() {
        AtomicBoolean writerEntered = new AtomicBoolean();
        OutputStreamPublisher p = publisher(Runnable::run, out -> writerEntered.set(true));
        Recorder r = new Recorder(1);
        p.subscribe(r);
        Throwable t = Assertions.assertThrows(Exception.class, r.done::join).getCause();
        Assertions.assertInstanceOf(IllegalStateException.class, t);
        Assertions.assertFalse(writerEntered.get());
    }

    @Test
    void aWriterOnAnEventLoopFails() {
        AtomicBoolean writerEntered = new AtomicBoolean();
        OutputStreamPublisher p = new OutputStreamPublisher(NEW_THREAD, out -> writerEntered.set(true),
            ReadBufferFactory.getJdkFactory(), () -> true);
        Recorder r = new Recorder(1);
        p.subscribe(r);
        Throwable t = Assertions.assertThrows(Exception.class, r.done::join).getCause();
        Assertions.assertInstanceOf(IllegalStateException.class, t);
        Assertions.assertFalse(writerEntered.get());
    }

    @Test
    void smallWritesThatOverflowTheChunkAreCollected() {
        OutputStreamPublisher p = publisher(NEW_THREAD, out -> {
            byte[] piece = new byte[500];
            for (int i = 0; i < 40; i++) {
                out.write(piece);
            }
        });
        Recorder r = new Recorder(Long.MAX_VALUE);
        p.subscribe(r);
        r.done.join();
        // 20000 bytes: 8000 collected, flushed before the overflowing write, which starts the next chunk
        Assertions.assertEquals(List.of(8000, 8000, 4000), r.items);
    }

    @Test
    void largeWritesArePublishedInChunkSlicesWithBackpressure() throws Exception {
        int size = OutputStreamPublisher.CHUNK_SIZE * 3 + 10;
        OutputStreamPublisher p = publisher(NEW_THREAD, out -> {
            out.write(new byte[3]);
            out.write(new byte[size]);
        });
        Recorder r = new Recorder(2);
        p.subscribe(r);
        while (r.items.size() < 2) {
            Thread.onSpinWait();
        }
        Thread.sleep(50);
        // no demand for the rest of the write
        Assertions.assertEquals(List.of(3, OutputStreamPublisher.CHUNK_SIZE), r.items);
        r.subscription.request(Long.MAX_VALUE);
        r.done.join();
        Assertions.assertEquals(List.of(3, OutputStreamPublisher.CHUNK_SIZE, OutputStreamPublisher.CHUNK_SIZE,
            OutputStreamPublisher.CHUNK_SIZE, 10), r.items);
    }

    @Test
    void singleByteWritesArePublishedInFullChunks() {
        OutputStreamPublisher p = publisher(NEW_THREAD, out -> {
            for (int i = 0; i < OutputStreamPublisher.CHUNK_SIZE + 1; i++) {
                out.write(i);
            }
        });
        Recorder r = new Recorder(Long.MAX_VALUE);
        p.subscribe(r);
        r.done.join();
        Assertions.assertEquals(List.of(OutputStreamPublisher.CHUNK_SIZE, 1), r.items);
    }

    @Test
    void emptyWritesAndRepeatedClosesAreIgnoredAndWritesAfterCloseFail() {
        CompletableFuture<Throwable> afterClose = new CompletableFuture<>();
        OutputStreamPublisher p = publisher(NEW_THREAD, out -> {
            out.write(new byte[4], 2, 0);
            out.write(new byte[]{1, 2, 3});
            out.close();
            out.close();
            try {
                out.write(4);
            } catch (IOException e) {
                afterClose.complete(e);
            }
        });
        Recorder r = new Recorder(Long.MAX_VALUE);
        p.subscribe(r);
        r.done.join();
        Assertions.assertEquals(List.of(3), r.items);
        Assertions.assertInstanceOf(IOException.class, afterClose.join());
    }

    @Test
    void anInterruptedWriterFailsWithInterruptedIOException() throws Exception {
        CompletableFuture<Thread> writerThread = new CompletableFuture<>();
        OutputStreamPublisher p = publisher(r -> new Thread(r).start(), out -> {
            writerThread.complete(Thread.currentThread());
            out.write(new byte[OutputStreamPublisher.CHUNK_SIZE]); // blocks, no demand
        });
        Recorder r = new Recorder(0);
        p.subscribe(r);
        Thread t = writerThread.get(5, TimeUnit.SECONDS);
        while (t.getState() != Thread.State.WAITING) {
            Thread.onSpinWait();
        }
        t.interrupt();
        Throwable failure = Assertions.assertThrows(Exception.class, r.done::join).getCause();
        Assertions.assertInstanceOf(InterruptedIOException.class, failure);
        Assertions.assertTrue(r.items.isEmpty());
    }

    private static final class Recorder implements Subscriber<ReadBuffer> {
        final long initialDemand;
        final List<Integer> items = new CopyOnWriteArrayList<>();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        final CompletableFuture<Void> done = new CompletableFuture<>();
        volatile Subscription subscription;

        Recorder(long initialDemand) {
            this.initialDemand = initialDemand;
        }

        @Override
        public void onSubscribe(Subscription s) {
            subscription = s;
            if (initialDemand > 0) {
                s.request(initialDemand);
            }
        }

        @Override
        public void onNext(ReadBuffer buffer) {
            items.add(buffer.readable());
            buffer.close();
        }

        @Override
        public void onError(Throwable t) {
            errors.add(t);
            done.completeExceptionally(t);
        }

        @Override
        public void onComplete() {
            done.complete(null);
        }
    }
}
