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

/**
 * Edge cases of {@link OutputStreamPublisher}: protocol violations, executor rejection,
 * stream misuse and interruption of a blocked writer.
 */
@Timeout(10)
class OutputStreamPublisherEdgeCaseTest {

    private static OutputStreamPublisher publisher(java.util.concurrent.Executor executor, ThrowingConsumer<? super OutputStream, ?> writer) {
        return new OutputStreamPublisher(executor, writer, ReadBufferFactory.getJdkFactory());
    }

    @Test
    void aSecondSubscriberIsRejected() {
        OutputStreamPublisher p = publisher(Runnable::run, out -> { });
        Recorder first = new Recorder(Long.MAX_VALUE);
        p.subscribe(first);
        Assertions.assertTrue(first.done.isDone());
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
        CompletableFuture<Throwable> writerFailure = new CompletableFuture<>();
        CompletableFuture<Runnable> task = new CompletableFuture<>();
        OutputStreamPublisher p = publisher(task::complete, out -> {
            try {
                out.write(1);
                out.flush();
            } catch (Throwable t) {
                writerFailure.complete(t);
                throw t;
            }
        });
        Recorder r = new Recorder(0);
        p.subscribe(r);
        r.subscription.request(0);
        Throwable t = Assertions.assertThrows(Exception.class, r.done::join).getCause();
        Assertions.assertInstanceOf(IllegalArgumentException.class, t);
        // a second violation is not signalled again
        r.subscription.request(-1);
        task.get().run();
        Assertions.assertTrue(writerFailure.isDone() || r.items.isEmpty());
        Assertions.assertEquals(1, r.errors.size());
    }

    @Test
    void singleByteWritesArePublishedInFullChunks() {
        OutputStreamPublisher p = publisher(Runnable::run, out -> {
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
        OutputStreamPublisher p = publisher(Runnable::run, out -> {
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
