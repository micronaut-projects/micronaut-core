/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.util.functional.ThrowingConsumer;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

/**
 * A publisher of the bytes a writer writes to an {@link OutputStream}, see
 * {@link ByteBodyFactory#streaming(Executor, ThrowingConsumer)}. The writer runs on the executor
 * once the publisher is subscribed, and blocks in a write while the subscriber has no demand.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class OutputStreamPublisher implements Publisher<ReadBuffer> {
    /**
     * Bytes written in smaller pieces are collected up to this size before they are published.
     */
    static final int CHUNK_SIZE = 8192;

    private final Executor executor;
    private final ThrowingConsumer<? super OutputStream, ?> writer;
    private final ReadBufferFactory readBufferFactory;
    private final BooleanSupplier isEventLoopThread;
    private boolean subscribed;

    OutputStreamPublisher(Executor executor, ThrowingConsumer<? super OutputStream, ?> writer, ReadBufferFactory readBufferFactory, BooleanSupplier isEventLoopThread) {
        this.isEventLoopThread = isEventLoopThread;
        this.executor = executor;
        this.writer = writer;
        this.readBufferFactory = readBufferFactory;
    }

    @Override
    public void subscribe(Subscriber<? super ReadBuffer> subscriber) {
        synchronized (this) {
            if (subscribed) {
                throw new IllegalStateException("Only one subscriber allowed");
            }
            subscribed = true;
        }
        Stream stream = new Stream(subscriber, Thread.currentThread());
        subscriber.onSubscribe(stream);
        try {
            executor.execute(stream::run);
        } catch (RuntimeException e) {
            stream.finish(e);
        }
    }

    private final class Stream extends OutputStream implements Subscription {
        private final Subscriber<? super ReadBuffer> subscriber;
        private final Thread subscribingThread;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition demandAvailable = lock.newCondition();
        private long demand;
        private boolean cancelled;
        private boolean closed;
        private boolean terminated;
        private byte @Nullable [] chunk;
        private int chunkLength;

        Stream(Subscriber<? super ReadBuffer> subscriber, Thread subscribingThread) {
            this.subscriber = subscriber;
            this.subscribingThread = subscribingThread;
        }

        void run() {
            lock.lock();
            try {
                if (cancelled) {
                    // the body was discarded before it was read, e.g. closed
                    terminated = true;
                    return;
                }
            } finally {
                lock.unlock();
            }
            Throwable failure = null;
            try {
                checkThread();
                writer.accept(this);
                close();
            } catch (Throwable t) { // NOSONAR an Error of the writer must still fail the subscriber, or it waits forever
                failure = t;
            }
            finish(failure);
        }

        private void checkThread() {
            if (Thread.currentThread() == subscribingThread) {
                throw new IllegalStateException("The writer of a streaming body must not run on the thread that reads the body, use an executor that runs it on another thread");
            }
            if (isEventLoopThread.getAsBoolean()) {
                throw new IllegalStateException("The writer of a streaming body must not run on an event loop, use a blocking executor");
            }
        }

        void finish(@Nullable Throwable failure) {
            lock.lock();
            try {
                if (terminated) {
                    return;
                }
                terminated = true;
                closed = true;
                if (cancelled) {
                    return;
                }
            } finally {
                lock.unlock();
            }
            if (failure == null) {
                subscriber.onComplete();
            } else {
                subscriber.onError(failure);
            }
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                // reactive streams rule 3.9: fail the subscriber and stop the writer
                boolean signal;
                lock.lock();
                try {
                    signal = !terminated && !cancelled;
                    cancelled = true;
                    terminated = true;
                    demandAvailable.signalAll();
                } finally {
                    lock.unlock();
                }
                if (signal) {
                    subscriber.onError(new IllegalArgumentException("Non-positive request: " + n));
                }
                return;
            }
            lock.lock();
            try {
                demand = demand + n < demand ? Long.MAX_VALUE : demand + n;
                demandAvailable.signalAll();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void cancel() {
            lock.lock();
            try {
                cancelled = true;
                demandAvailable.signalAll();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void write(int b) throws IOException {
            ensureOpen();
            byte[] c = chunk;
            if (c == null) {
                c = new byte[CHUNK_SIZE];
                chunk = c;
            }
            c[chunkLength++] = (byte) b;
            if (chunkLength == c.length) {
                flushChunk();
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            Objects.checkFromIndexSize(off, len, b.length);
            ensureOpen();
            if (len == 0) {
                return;
            }
            if (chunkLength + len > CHUNK_SIZE) {
                flushChunk();
            }
            // a write of at least a chunk is published in chunk sized slices, with backpressure
            // between them, instead of as one copy of the whole write
            while (len >= CHUNK_SIZE) {
                byte[] slice = new byte[CHUNK_SIZE];
                System.arraycopy(b, off, slice, 0, CHUNK_SIZE);
                publish(readBufferFactory.adapt(slice));
                off += CHUNK_SIZE;
                len -= CHUNK_SIZE;
            }
            if (len > 0) {
                byte[] c = chunk;
                if (c == null) {
                    c = new byte[CHUNK_SIZE];
                    chunk = c;
                }
                System.arraycopy(b, off, c, chunkLength, len);
                chunkLength += len;
                if (chunkLength == CHUNK_SIZE) {
                    flushChunk();
                }
            }
        }

        @Override
        public void flush() throws IOException {
            ensureOpen();
            flushChunk();
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            flushChunk();
            closed = true;
        }

        private void ensureOpen() throws IOException {
            if (closed) {
                throw new IOException("Stream closed");
            }
        }

        private void flushChunk() throws IOException {
            if (chunkLength == 0) {
                return;
            }
            byte[] c = Objects.requireNonNull(chunk);
            int length = chunkLength;
            chunkLength = 0;
            byte[] published;
            if (length == c.length) {
                // hand the full chunk over, the next write allocates a new one
                published = c;
                chunk = null;
            } else {
                published = Arrays.copyOf(c, length);
            }
            publish(readBufferFactory.adapt(published));
        }

        private void publish(ReadBuffer buffer) throws IOException {
            lock.lock();
            try {
                while (demand <= 0 && !cancelled) {
                    demandAvailable.await();
                }
                if (cancelled) {
                    buffer.close();
                    throw ByteBody.BodyDiscardedException.create();
                }
                demand--;
            } catch (InterruptedException e) {
                buffer.close();
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Interrupted while waiting for the reader of the body");
            } finally {
                lock.unlock();
            }
            subscriber.onNext(buffer);
        }
    }
}
