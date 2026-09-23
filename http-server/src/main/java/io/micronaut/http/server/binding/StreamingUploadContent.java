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
import io.micronaut.http.MediaType;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.multipart.FormFieldMetadata;
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
import java.util.concurrent.CompletionException;
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

    private static final String REQUEST_BODY = "request body";

    private final RawFormField field;
    private final boolean requestBody;
    private volatile long completeSize = -1;

    StreamingUploadContent(RawFormField field, UploadContext context) {
        this(field, context, false);
    }

    private StreamingUploadContent(RawFormField field, UploadContext context, boolean requestBody) {
        super(field.metadata(), context);
        this.field = field;
        this.requestBody = requestBody;
    }

    /**
     * The whole body of a request, read like the content of a form field: into memory with a
     * limit, or to a file. It has no limit of its own.
     *
     * @param body        The body, owned by the content
     * @param contentType The content type of the request
     * @param context     The context
     * @return The content
     */
    static StreamingUploadContent requestBody(CloseableByteBody body, @Nullable MediaType contentType, UploadContext context) {
        return new StreamingUploadContent(new RawFormField(new FormFieldMetadata(REQUEST_BODY, null, contentType), body), context, true);
    }

    @Override
    String describe() {
        return requestBody ? REQUEST_BODY : super.describe();
    }

    /**
     * @param limit    The limit
     * @param received The bytes received so far
     * @return The failure for content over the limit: like a buffered request body that is too
     * large for the request body, and naming the field for a form field
     */
    private ContentLengthExceededException tooLarge(long limit, long received) {
        return requestBody ? new ContentLengthExceededException(limit, received) : tooLarge(name(), limit);
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
    UploadContent.Operation<Void> newStreamTransfer(OutputStream out) {
        return new StreamTransfer(out, fieldLimit());
    }

    @Override
    boolean isComplete() {
        return false;
    }

    @Override
    byte[] readComplete() {
        throw new IllegalStateException("The content of " + describe() + " is still arriving");
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

    /**
     * Subscribe an operation to the content. A body that cannot be read fails the operation like
     * the upstream would.
     *
     * @param subscriber The operation
     */
    private void subscribe(Subscriber<ReadBuffer> subscriber) {
        Publisher<ReadBuffer> source;
        try {
            source = field.byteBody().toReadBufferPublisher();
        } catch (Throwable e) {
            subscriber.onError(e);
            return;
        }
        source.subscribe(subscriber);
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
        // start() subscribes, or subscribed: only onSubscribe may cancel the upstream, and the
        // field must not be closed while the body is claimed
        private boolean subscribes;

        Collect(long limit) {
            this.limit = limit;
        }

        @Override
        void start() {
            synchronized (this) {
                if (done) {
                    return;
                }
                subscribes = true;
            }
            subscribe(this);
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
                settle(null, tooLarge(limit, total), null);
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
            boolean subscribing;
            synchronized (this) {
                if (done) {
                    return;
                }
                done = true;
                discard = buffers;
                buffers = null;
                s = subscription;
                subscribing = subscribes;
            }
            if (s != null) {
                s.cancel();
            } else if (!subscribing) {
                // never subscribes: discard the content. Otherwise onSubscribe cancels
                field.close();
            }
            closeAll(discard);
            settle(null, new CancellationException("The " + describe() + " was closed"), null);
        }
    }

    /**
     * Writes the content to a stream on the thread that delivers it, one buffer at a time: the
     * next buffer is requested once the previous one was written. The stream is flushed at the
     * end, and not closed.
     */
    private final class StreamTransfer extends UploadContent.Operation<Void> implements Subscriber<ReadBuffer> {
        private final OutputStream out;
        private final long limit;
        // guarded by this
        private @Nullable Subscription subscription;
        private long total;
        private boolean done;
        // see Collect
        private boolean subscribes;

        StreamTransfer(OutputStream out, long limit) {
            this.out = out;
            this.limit = limit;
        }

        @Override
        void start() {
            synchronized (this) {
                if (done) {
                    return;
                }
                subscribes = true;
            }
            subscribe(this);
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
            Subscription s;
            long received;
            synchronized (this) {
                if (done) {
                    buffer.close();
                    return;
                }
                total += buffer.readable();
                received = total;
                s = subscription;
                if (received > limit) {
                    done = true;
                }
            }
            Throwable error = null;
            if (received > limit) {
                buffer.close();
                error = tooLarge(limit, received);
            } else {
                try {
                    // consuming
                    buffer.transferTo(out);
                } catch (IOException | RuntimeException e) {
                    buffer.close();
                    synchronized (this) {
                        done = true;
                    }
                    error = e;
                }
            }
            if (error != null) {
                if (s != null) {
                    s.cancel();
                }
                settle(null, error, null);
            } else if (s != null) {
                s.request(1);
            }
        }

        @Override
        public void onError(Throwable t) {
            synchronized (this) {
                if (done) {
                    return;
                }
                done = true;
            }
            settle(null, t, null);
        }

        @Override
        public void onComplete() {
            long size;
            synchronized (this) {
                if (done) {
                    return;
                }
                done = true;
                size = total;
            }
            try {
                out.flush();
            } catch (IOException | RuntimeException e) {
                settle(null, e, null);
                return;
            }
            completeSize = size;
            settle(null, null, null);
        }

        @Override
        void abort() {
            Subscription s;
            boolean subscribing;
            synchronized (this) {
                if (done) {
                    return;
                }
                done = true;
                s = subscription;
                subscribing = subscribes;
            }
            if (s != null) {
                s.cancel();
            } else if (!subscribing) {
                // never subscribes: discard the content. Otherwise onSubscribe cancels
                field.close();
            }
            settle(null, new CancellationException("The " + describe() + " was closed"), null);
        }
    }

    /**
     * Writes the content to a staging file on the I/O executor, one buffer at a time, and
     * publishes it. The disk tasks run in sequence: each one is chained to the previous one.
     * Completion of the upstream and completion of the transfer are separate: a write that fails
     * after the last buffer arrived still fails the transfer, and nothing is published.
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
        private boolean upstreamDone;
        // failed or aborted: the queued disk tasks are skipped, and the transfer settles with the failure
        private boolean stopped;
        // the one outcome of the transfer is being settled
        private boolean settling;
        // start() subscribes, or subscribed: only onSubscribe may cancel the upstream, and the
        // field must not be closed while the body is claimed on another thread
        private boolean subscribes;
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
                if (stopped) {
                    return;
                }
                subscribes = true;
                // queued before a failure can queue the cleanup, which then deletes the file
                enqueue(() -> {
                    synchronized (this) {
                        if (stopped) {
                            return;
                        }
                    }
                    try {
                        staging = createStaging(destination);
                        out = Files.newOutputStream(staging);
                    } catch (IOException | RuntimeException e) {
                        fail(e, true);
                    }
                });
            }
            subscribe(this);
        }

        private void enqueue(Runnable task) {
            CompletableFuture<?> next;
            synchronized (this) {
                next = tail.handleAsync((ignored, error) -> {
                    task.run();
                    return null;
                }, executor);
                tail = next;
            }
            // the tasks handle their own failures: a failed task means the executor rejected it
            next.whenComplete((ignored, error) -> {
                if (error != null) {
                    rejected(error instanceof CompletionException && error.getCause() != null ? error.getCause() : error);
                }
            });
        }

        @Override
        public void onSubscribe(Subscription s) {
            boolean cancel;
            synchronized (this) {
                cancel = upstreamDone;
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
                if (upstreamDone || stopped) {
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
                if (upstreamDone) {
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
                fail(tooLarge(limit, total), true);
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
                if (upstreamDone) {
                    return;
                }
                // no more buffers: closing now waits for the publication instead of aborting it,
                // but a queued write that fails still fails the transfer
                upstreamDone = true;
                size = total;
            }
            enqueue(() -> {
                synchronized (this) {
                    if (stopped) {
                        // a write failed: the cleanup task queued by fail() settles the transfer
                        return;
                    }
                }
                Throwable error = null;
                try {
                    OutputStream o = out;
                    Path file = staging;
                    if (o == null || file == null) {
                        throw new IOException("The staging file of " + describe() + " was not created");
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
                settleOnce(null, error, cleanupError);
            });
        }

        @Override
        void abort() {
            synchronized (this) {
                if (upstreamDone && !stopped) {
                    // every buffer arrived: the publication completes
                    return;
                }
            }
            fail(new CancellationException("The " + describe() + " was closed"), true);
        }

        /**
         * Stop: cancel the upstream, then close and delete the staging file after the disk tasks
         * that were queued, and settle with the failure. A failure after the upstream completed
         * stops the publication too.
         */
        private void fail(Throwable error, boolean cancel) {
            Subscription s;
            boolean upstreamWasDone;
            boolean subscribing;
            synchronized (this) {
                if (stopped) {
                    return;
                }
                stopped = true;
                upstreamWasDone = upstreamDone;
                upstreamDone = true;
                s = subscription;
                subscribing = subscribes;
            }
            if (cancel && !upstreamWasDone) {
                cancelUpstream(s, subscribing);
            }
            enqueue(() -> settleOnce(null, error, releaseStaging(null)));
        }

        /**
         * The executor rejected a disk task: nothing queued after it runs. Release the staging
         * file on this thread, as the executor cannot, and settle, so that neither the transfer
         * nor closing the upload waits forever.
         */
        private void rejected(Throwable error) {
            Subscription s;
            boolean upstreamWasDone;
            boolean subscribing;
            synchronized (this) {
                if (settling) {
                    return;
                }
                stopped = true;
                upstreamWasDone = upstreamDone;
                upstreamDone = true;
                s = subscription;
                subscribing = subscribes;
            }
            if (!upstreamWasDone) {
                cancelUpstream(s, subscribing);
            }
            settleOnce(null, error, releaseStaging(null));
        }

        /**
         * @param s           The subscription, if there is one yet
         * @param subscribing Whether start() subscribes or subscribed: then onSubscribe cancels,
         *                    as the upstream is done, and closing the field here would race
         *                    with the claim of its body
         */
        private void cancelUpstream(@Nullable Subscription s, boolean subscribing) {
            if (s != null) {
                s.cancel();
            } else if (!subscribing) {
                // never subscribes: discard the content
                field.close();
            }
        }

        private @Nullable Throwable releaseStaging(@Nullable Throwable cleanupError) {
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
            return cleanupError;
        }

        private void settleOnce(@Nullable Void value, @Nullable Throwable error, @Nullable Throwable cleanupError) {
            synchronized (this) {
                if (settling) {
                    return;
                }
                settling = true;
            }
            settle(value, error, cleanupError);
        }
    }
}
