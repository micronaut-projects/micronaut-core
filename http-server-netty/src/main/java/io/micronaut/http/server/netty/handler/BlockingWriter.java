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
package io.micronaut.http.server.netty.handler;

import io.micronaut.core.annotation.Internal;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * This class writes from a {@link InputStream} to a netty channel with backpressure. There's
 * separate implementations for HTTP/1.1 and HTTP/2.
 *
 * @since 4.4.0
 * @author Jonas Konrad
 */
@Internal
abstract class BlockingWriter {
    static final int QUEUE_SIZE = 2;
    static final int CHUNK_SIZE = 64 * 1024;

    private static final Logger LOG = LoggerFactory.getLogger(BlockingWriter.class);

    private final ByteBufAllocator alloc;
    private final InputStream stream;
    private final ExecutorService blockingExecutor;

    private final Queue<ByteBuf> queue = new ArrayDeque<>(QUEUE_SIZE);
    private Future<?> worker = null;
    private boolean workerReady = false;
    private boolean discard = false;
    private boolean done = false;
    private boolean producerWaiting = false;
    private boolean consumerWaiting = false;

    BlockingWriter(
            ByteBufAllocator alloc,
            InputStream stream,
            ExecutorService blockingExecutor) {
        this.alloc = alloc;
        this.stream = stream;
        this.blockingExecutor = blockingExecutor;
    }

    /**
     * Write the message start. Called on the event loop.
     */
    protected abstract void writeStart();

    /**
     * Write some data. Called on the event loop.
     *
     * @param buf The buffer
     * @return {@code true} iff we can continue writing immediately, {@code false} iff we should
     * wait for the next call to {@link #writeSome}
     */
    protected abstract boolean writeData(ByteBuf buf);

    /**
     * Write the message end. Called on the event loop.
     */
    protected abstract void writeLast();

    /**
     * Asynchronously request a call to {@link #writeSome()} on the event loop. Called from the IO
     * worker thread.
     */
    protected abstract void writeSomeAsync();

    /**
     * Call from the event loop when the channel becomes writable or {@link #writeSomeAsync()} is
     * called.
     */
    final void writeSome() {
        if (worker == null) {
            writeStart();
            worker = blockingExecutor.submit(this::work);
        }
        while (true) {
            ByteBuf msg;
            synchronized (this) {
                if (producerWaiting) {
                    producerWaiting = false;
                    notifyAll();
                }
                msg = queue.poll();
                if (msg == null && !this.done) {
                    consumerWaiting = true;
                    break;
                }
            }
            if (msg == null) {
                // this.done == true inside the synchronized block
                writeLast();
                break;
            } else {
                if (!writeData(msg)) {
                    break;
                }
            }
        }
    }

    void discard() {
        discard = true;
        if (worker == null) {
            worker = blockingExecutor.submit(this::work);
        } else {
            synchronized (this) {
                if (workerReady) {
                    worker.cancel(true);
                    // in case the worker was already done, drain buffers
                    drain();
                } // else worker is still setting up and will see the discard flag in due time
            }
        }
    }

    private void work() {
        ByteBuf buf = null;
        try (InputStream stream = this.stream) {
            synchronized (this) {
                this.workerReady = true;
                if (this.discard) {
                    // don't read
                    return;
                }
            }
            while (true) {
                buf = alloc.heapBuffer(CHUNK_SIZE);
                int n = buf.writeBytes(stream, CHUNK_SIZE);
                synchronized (this) {
                    if (n == -1) {
                        done = true;
                        wakeConsumer();
                        break;
                    }
                    while (queue.size() >= QUEUE_SIZE && !discard) {
                        producerWaiting = true;
                        wait();
                    }
                    if (discard) {
                        break;
                    }
                    queue.add(buf);
                    // buf is now owned by the queue
                    buf = null;

                    wakeConsumer();
                }
            }
        } catch (InterruptedException | InterruptedIOException ignored) {
        } catch (Exception e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("InputStream threw an error during read. This error cannot be forwarded to the client. Please make sure any errors are thrown by the controller instead.", e);
            }
        } finally {
            // if we failed to add a buffer to the queue, release it
            if (buf != null) {
                buf.release();
            }
            synchronized (this) {
                done = true;

                if (discard) {
                    drain();
                }
            }
        }
    }

    private void wakeConsumer() {
        assert Thread.holdsLock(this);

        if (!discard && consumerWaiting) {
            consumerWaiting = false;
            writeSomeAsync();
        }
    }

    private void drain() {
        assert Thread.holdsLock(this);

        ByteBuf buf;
        while (true) {
            buf = queue.poll();
            if (buf != null) {
                buf.release();
            } else {
                break;
            }
        }
    }
}
