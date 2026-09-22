/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.server.binding;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.multipart.RawFormField;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The content of a form field that is still arriving. Nothing is read before an operation
 * subscribes to it; the demand is one buffer at a time.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class StreamingUploadContent extends UploadContent {

    private final RawFormField field;
    private volatile long completeSize = -1;

    StreamingUploadContent(RawFormField field, UploadContext context) {
        super(field.metadata(), context);
        this.field = field;
    }

    @Override
    OptionalLong size() {
        long size = completeSize;
        return size < 0 ? OptionalLong.empty() : OptionalLong.of(size);
    }

    @Override
    OptionalLong expectedSize() {
        return field.byteBody().expectedLength();
    }

    @Override
    UploadContent.Operation<byte[]> newBytes(long limit) {
        return new Collect(limit);
    }

    @Override
    UploadContent.Operation<Void> newTransfer(Path destination) {
        return new Transfer(destination, fieldLimit(), context.ioExecutor());
    }

    @Override
    CloseableByteBody moveBody() {
        return field.byteBody().move();
    }

    @Override
    CompletableFuture<Void> release() {
        // the form decoder discards the rest of the part
        field.close();
        return CompletableFuture.completedFuture(null);
    }

    private Publisher<ReadBuffer> source() {
        return field.byteBody().toReadBufferPublisher();
    }

    private static void closeAll(@Nullable List<ReadBuffer> buffers) {
        if (buffers != null) {
            for (ReadBuffer buffer : buffers) {
                buffer.close();
            }
        }
    }

    /**
     * Collects the content in memory, up to a limit.
     */
    private final class Collect extends UploadContent.Operation<byte[]> implements Subscriber<ReadBuffer> {
        private final long limit;
        // guarded by this
        private @Nullable Subscription subscription;
        private @Nullable List<ReadBuffer> buffers = new ArrayList<>();
        private long total;
        private boolean done;

        Collect(long limit) {
            this.limit = limit;
        }

        @Override
        void start() {
            synchronized (this) {
                if (done) {
                    return;
                }
            }
            source().subscribe(this);
        }

        @Override
        public void onSubscribe(Subscription s) {
            boolean cancel;
            synchronized (this) {
                cancel = done;
                subscription = s;
            }
            if (cancel) {
                s.cancel();
            } else {
                s.request(1);
            }
        }

        @Override
        public void onNext(ReadBuffer buffer) {
            List<ReadBuffer> discard = null;
            Subscription s;
            synchronized (this) {
                if (done) {
                    buffer.close();
                    return;
                }
                total += buffer.readable();
                s = subscription;
                if (total > limit) {
                    done = true;
                    discard = buffers;
                    buffers = null;
                } else {
                    Objects.requireNonNull(buffers).add(buffer);
                }
            }
            if (discard != null) {
                buffer.close();
                closeAll(discard);
                if (s != null) {
                    s.cancel();
                }
                settle(null, tooLarge(name(), limit), null);
            } else if (s != null) {
                s.request(1);
            }
        }

        @Override
        public void onError(Throwable t) {
            List<ReadBuffer> discard;
            synchronized (this) {
                if (done) {
                    return;
                }
                done = true;
                discard = buffers;
                buffers = null;
            }
            closeAll(discard);
            settle(null, t, null);
        }

        @Override
        public void onComplete() {
            List<ReadBuffer> collected;
            int size;
            synchronized (this) {
                if (done) {
                    return;
                }
                done = true;
                collected = buffers;
                buffers = null;
                size = (int) total;
            }
            byte[] bytes = new byte[size];
            int offset = 0;
            if (collected != null) {
                for (ReadBuffer buffer : collected) {
                    int readable = buffer.readable();
                    // consuming
                    buffer.toArray(bytes, offset);
                    offset += readable;
                }
            }
            completeSize = size;
            settle(bytes, null, null);
        }

        @Override
        void abort() {
            List<ReadBuffer> discard;
            Subscription s;
            synchronized (this) {
                if (done) {
                    return;
                }
                done = true;
                discard = buffers;
                buffers = null;
                s = subscription;
            }
            if (s != null) {
                s.cancel();
            } else {
                // never subscribed: discard the content
                field.close();
            }
            closeAll(discard);
            settle(null, new CancellationException("The form field " + name() + " was closed"), null);
        }
    }

    /**
     * Writes the content to a staging file on the I/O executor, one buffer at a time, and
     * publishes it. The disk tasks run in sequence: each one is chained to the previous one.
     */
    private final class Transfer extends UploadContent.Operation<Void> implements Subscriber<ReadBuffer> {
        private final Path destination;
        private final long limit;
        private final Executor executor;
        // guarded by this
        private @Nullable Subscription subscription;
        private CompletableFuture<?> tail = CompletableFuture.completedFuture(null);
        private long total;
        // no more upstream signals are handled: completed, failed or aborted
        private boolean done;
        // failed or aborted: the queued writes are skipped
        private boolean stopped;
        // only used by the disk tasks, which run in sequence
        private @Nullable Path staging;
        private @Nullable OutputStream out;

        Transfer(Path destination, long limit, Executor executor) {
            this.destination = destination;
            this.limit = limit;
            this.executor = executor;
        }

        @Override
        void start() {
            synchronized (this) {
                if (done) {
                    return;
                }
            }
            enqueue(() -> {
                try {
                    staging = createStaging(destination);
                    out = Files.newOutputStream(staging);
                } catch (IOException | RuntimeException e) {
                    fail(e, true);
                }
            });
            source().subscribe(this);
        }

        private void enqueue(Runnable task) {
            synchronized (this) {
                tail = tail.handleAsync((ignored, error) -> {
                    task.run();
                    return null;
                }, executor);
            }
        }

        @Override
        public void onSubscribe(Subscription s) {
            boolean cancel;
            synchronized (this) {
                cancel = done;
                subscription = s;
            }
            if (cancel) {
                s.cancel();
                return;
            }
            // the first buffer is requested once the staging file is open
            enqueue(() -> request(s));
        }

        private void request(Subscription s) {
            synchronized (this) {
                if (done || stopped) {
                    return;
                }
            }
            s.request(1);
        }

        @Override
        public void onNext(ReadBuffer buffer) {
            Subscription s;
            boolean tooLarge = false;
            synchronized (this) {
                if (done) {
                    buffer.close();
                    return;
                }
                total += buffer.readable();
                s = subscription;
                if (total > limit) {
                    tooLarge = true;
                }
            }
            if (tooLarge) {
                buffer.close();
                fail(tooLarge(name(), limit), true);
                return;
            }
            enqueue(() -> {
                synchronized (this) {
                    if (stopped) {
                        buffer.close();
                        return;
                    }
                }
                try {
                    OutputStream o = out;
                    if (o == null) {
                        buffer.close();
                        return;
                    }
                    // consuming
                    buffer.transferTo(o);
                } catch (IOException | RuntimeException e) {
                    buffer.close();
                    fail(e, true);
                    return;
                }
                if (s != null) {
                    request(s);
                }
            });
        }

        @Override
        public void onError(Throwable t) {
            fail(t, false);
        }

        @Override
        public void onComplete() {
            long size;
            synchronized (this) {
                if (done) {
                    return;
                }
                // from now on, closing waits for the publication instead of aborting it
                done = true;
                size = total;
            }
            enqueue(() -> {
                Throwable error = null;
                try {
                    OutputStream o = out;
                    Path file = staging;
                    if (o == null || file == null) {
                        throw new IOException("The staging file of form field " + name() + " was not created");
                    }
                    out = null;
                    o.close();
                    publish(file, destination);
                    staging = null;
                } catch (IOException | RuntimeException e) {
                    error = e;
                }
                Throwable cleanupError = null;
                if (error != null) {
                    cleanupError = deleteQuietly(staging, null);
                    staging = null;
                } else {
                    completeSize = size;
                }
                settle(null, error, cleanupError);
            });
        }

        @Override
        void abort() {
            fail(new CancellationException("The form field " + name() + " was closed"), true);
        }

        /**
         * Stop: cancel the upstream, then close and delete the staging file after the disk tasks
         * that were queued.
         */
        private void fail(Throwable error, boolean cancel) {
            Subscription s;
            synchronized (this) {
                if (done) {
                    return;
                }
                done = true;
                stopped = true;
                s = subscription;
            }
            if (cancel) {
                if (s != null) {
                    s.cancel();
                } else {
                    field.close();
                }
            }
            enqueue(() -> {
                Throwable cleanupError = null;
                OutputStream o = out;
                out = null;
                if (o != null) {
                    try {
                        o.close();
                    } catch (IOException e) {
                        cleanupError = e;
                    }
                }
                cleanupError = deleteQuietly(staging, cleanupError);
                staging = null;
                settle(null, error, cleanupError);
            });
        }
    }
}
