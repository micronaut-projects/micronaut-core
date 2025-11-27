/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.server.multipart;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.body.stream.BaseSharedBuffer;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.multipart.CompletedAttribute;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import io.micronaut.http.multipart.FormFieldMetadata;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

// TODO: docs
@Internal
@Singleton
public final class FormFactory {
    private static final String COMPLETER = FormRouteCompleter.class.getName();
    private static final Logger LOG = LoggerFactory.getLogger(FormFactory.class);

    private final Executor diskWriteExecutor;
    private final HttpServerConfiguration configuration;

    FormFactory(@Named(TaskExecutors.BLOCKING) Executor diskWriteExecutor, HttpServerConfiguration configuration) {
        this.diskWriteExecutor = diskWriteExecutor;
        this.configuration = configuration;
    }

    public static FormRouteCompleter getCompleterOrNull(HttpRequest<?> request) {
        return request.getAttribute(COMPLETER, FormRouteCompleter.class).orElse(null);
    }

    public FormRouteCompleter getOrCreateCompleter(HttpRequest<?> request) {
        if (!(request instanceof FormCapableHttpRequest<?> fchr)) {
            throw new IllegalStateException("Request class " + request + " does not support form binding");
        }
        if (!fchr.hasFormBody()) {
            throw new IllegalStateException("Request does not have a form body");
        }
        FormRouteCompleter completer = getCompleterOrNull(request);
        if (completer != null) {
            return completer;
        }
        completer = new FormRouteCompleter(fchr);
        request.setAttribute(COMPLETER, completer);
        return completer;
    }

    public ExecutionFlow<? extends CompletedPart> completePart(@Nullable HttpRequest<?> request, @NonNull RawFormField formField) {
        if (formField.metadata().fileName() == null) {
            return completeAttribute(formField);
        } else {
            return completeFileUpload(request, formField);
        }
    }

    @NonNull
    public static ExecutionFlow<CompletedAttribute> completeAttribute(@NonNull RawFormField formField) {
        return InternalByteBody.bufferFlow(formField.byteBody()).map(av -> CompletedAttribute.create(formField.metadata(), av.toReadBuffer()));
    }

    /**
     * Buffer a streamed {@link RawFormField} into a {@link CompletedFileUpload}. May save data to
     * disk, if configured.
     *
     * @param request   Optional request, used to get at the ReadBufferFactory
     * @param formField The form field to stream
     * @return The flow with the uploaded file
     */
    @NonNull
    public ExecutionFlow<CompletedFileUpload> completeFileUpload(@Nullable HttpRequest<?> request, @NonNull RawFormField formField) {
        ToDiskSubscriber tds = new ToDiskSubscriber(formField.metadata(), bodyFactory(request).readBufferFactory(), null);
        Flux.from(formField.byteBody().toReadBufferPublisher()).subscribe(tds);
        return tds.result;
    }

    /**
     * Similar to {@link #completeFileUpload}, except you also get a live view of the data that is
     * written to disk, in form of a {@link CloseableByteBody}. This body will either forward data
     * directly as it arrives if consumers are fast enough, or replay it from disk if necessary.
     *
     * @param request   Optional request, used to get at the ReadBufferFactory
     * @param formField The form field to stream
     * @return The streaming upload
     */
    @NonNull
    public StreamingFileUpload streamFileUpload(@Nullable HttpRequest<?> request, @NonNull RawFormField formField) {
        LiveUploadObserver live = new LiveUploadObserver(bodyFactory(request));
        formField.byteBody().expectedLength().ifPresent(live.sharedBuffer::setExpectedLength);
        ToDiskSubscriber tds = new ToDiskSubscriber(formField.metadata(), bodyFactory(request).readBufferFactory(), live);
        Flux.from(formField.byteBody().toReadBufferPublisher()).subscribe(tds);
        return new StreamingFileUpload(formField.metadata(), tds.result, live.rootBody, diskWriteExecutor);
    }

    public void discardAsync(CompletedPart part) {
        diskWriteExecutor.execute(() -> {
            try {
                part.close();
            } catch (IOException e) {
                LOG.debug("Failed to close discarded part", e);
            }
        });
    }

    static ByteBodyFactory bodyFactory(@Nullable HttpRequest<?> request) {
        if (request instanceof ServerHttpRequest<?> shr) {
            return shr.byteBodyFactory();
        } else {
            return ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        }
    }

    private PathAndStream moveToDisk(List<ReadBuffer> memory) {
        Path tmp = null;
        OutputStream out = null;
        try {
            Optional<File> location = configuration.getMultipart().getLocation();
            if (location.isPresent()) {
                tmp = Files.createTempFile(location.get().toPath(), "FUp_", ".tmp");
            } else {
                tmp = Files.createTempFile("FUp_", ".tmp");
            }
            out = Files.newOutputStream(tmp);
            for (ReadBuffer rb : memory) {
                rb.transferTo(out);
            }
            return new PathAndStream(tmp, out);
        } catch (IOException e) {
            closeSafe(e, memory, out, tmp);
            throw new CompletionException(e);
        } catch (Throwable t) {
            closeSafe(t, memory, out, tmp);
            throw t;
        }
    }

    private static void closeSafe(Throwable ctx, @Nullable List<ReadBuffer> memory, @Nullable OutputStream out, @Nullable Path tmp) {
        if (memory != null) {
            for (ReadBuffer rb : memory) {
                rb.close();
            }
        }
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                ctx.addSuppressed(e);
            }
        }
        if (tmp != null) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException e) {
                ctx.addSuppressed(e);
            }
        }
    }

    /**
     * This subscriber collects form data and saves it to disk when necessary.
     */
    private final class ToDiskSubscriber implements Subscriber<ReadBuffer> {
        private final FormFieldMetadata metadata;
        private final ReadBufferFactory bufferFactory;

        private final DelayedExecutionFlow<CompletedFileUpload> result = DelayedExecutionFlow.create();
        private boolean completed;

        private Subscription subscription;
        private long total = 0;
        private List<ReadBuffer> memory = new ArrayList<>();
        private CompletableFuture<PathAndStream> file;
        private CompletableFuture<PathAndStream> latestPieceWritten;

        @Nullable
        private final LiveUploadObserver live;

        ToDiskSubscriber(FormFieldMetadata metadata, ReadBufferFactory bufferFactory, LiveUploadObserver live) {
            this.metadata = metadata;
            this.bufferFactory = bufferFactory;
            this.live = live;
        }

        @Override
        public void onSubscribe(Subscription s) {
            this.subscription = s;
            s.request(1);
        }

        @Override
        public void onNext(ReadBuffer buffer) {
            total += buffer.readable();
            var mc = configuration.getMultipart();
            // check size
            if (total > mc.getMaxFileSize()) {
                buffer.close();
                onError(new IOException("Size exceed allowed maximum capacity"));
                return;
            }
            if (file == null) {
                // do we need to transfer to disk?
                if (mc.isDisk() || (mc.isMixed() && total > mc.getThreshold())) {
                    List<ReadBuffer> memory = this.memory;
                    this.memory = null;
                    // transfer asynchronously
                    file = CompletableFuture.supplyAsync(() -> {
                        PathAndStream ps = moveToDisk(memory);
                        if (live != null) {
                            live.path = ps.path;
                        }
                        return ps;
                    }, diskWriteExecutor);
                    latestPieceWritten = file;
                } else {
                    // no transfer, just save to memory
                    if (live != null) {
                        live.receivedNoFile(buffer.duplicate());
                    }
                    memory.add(buffer);
                    subscription.request(1);
                    return;
                }
            }
            // might have to wait for disk transfer here
            latestPieceWritten = file.whenCompleteAsync((p, t) -> {
                if (t != null) {
                    // transfer failed, discard this piece also
                    buffer.close();
                    subscription.cancel();
                    result.tryCompleteExceptionally(t);
                    return;
                }
                try {
                    if (live == null) {
                        buffer.transferTo(p.out);
                    } else {
                        try {
                            buffer.duplicate().transferTo(p.out);
                        } catch (Throwable u) {
                            buffer.close();
                            throw u;
                        }
                        live.receivedWithFile(buffer);
                    }
                    // transfer complete, request the next piece
                    subscription.request(1);
                } catch (IOException e) {
                    // transfer of this piece failed
                    subscription.cancel();
                    result.tryCompleteExceptionally(e);
                    try {
                        p.close();
                    } catch (IOException ex) {
                        e.addSuppressed(ex);
                    }
                    throw new CompletionException(e);
                } catch (Throwable e) {
                    // transfer of this piece failed
                    subscription.cancel();
                    result.tryCompleteExceptionally(e);
                    try {
                        p.close();
                    } catch (IOException ex) {
                        e.addSuppressed(ex);
                    }
                    throw e;
                }
            }, diskWriteExecutor);
        }

        @Override
        public void onError(Throwable t) {
            // completed flag makes sure our "size exceeded" error call above doesn't clash
            if (completed) {
                return;
            }
            completed = true;
            if (live != null) {
                live.upstreamComplete(t);
            }
            if (latestPieceWritten != null) {
                // also asynchronously close the disk file
                latestPieceWritten.whenCompleteAsync((p, t2) -> {
                    if (t2 != null) {
                        t.addSuppressed(t2);
                    } else {
                        try {
                            p.close();
                        } catch (IOException e) {
                            t.addSuppressed(e);
                        }
                    }
                }, diskWriteExecutor);
            } else {
                // close memory buffers
                for (ReadBuffer readBuffer : memory) {
                    readBuffer.close();
                }
            }
            result.tryCompleteExceptionally(t);
        }

        @Override
        public void onComplete() {
            if (completed) {
                return;
            }
            if (live != null) {
                live.upstreamComplete(null);
            }
            assert (file == null) == (latestPieceWritten == null);
            if (latestPieceWritten == null) {
                // all in-memory
                result.complete(CompletedFileUpload.ofMemory(metadata, bufferFactory.compose(memory)));
            } else {
                // wait for last piece to be written
                latestPieceWritten.whenCompleteAsync((ps, t) -> {
                    if (t != null) {
                        // failed to write piece!
                        result.tryCompleteExceptionally(t);
                        return;
                    }
                    try {
                        // done writing, need to close the output stream
                        ps.out.close();
                    } catch (IOException e) {
                        // failed to close, also delete the file
                        try {
                            Files.deleteIfExists(ps.path);
                        } catch (IOException ex) {
                            e.addSuppressed(ex);
                        }
                        result.tryCompleteExceptionally(e);
                        return;
                    }
                    // done!
                    CompletedFileUpload cfu = CompletedFileUpload.ofFile(metadata, ps.path, total);
                    if (!result.tryComplete(cfu)) {
                        try {
                            cfu.close();
                        } catch (IOException e) {
                            LOG.debug("Failed to close cancelled CompletedFileUpload", e);
                        }
                    }
                }, diskWriteExecutor);
            }
        }
    }

    private class LiveUploadObserver implements BufferConsumer.Upstream {

        private final BaseSharedBuffer sharedBuffer;
        private final CloseableByteBody rootBody;
        private final ReadBufferFactory readBufferFactory;

        private final AtomicReference<State> state = new AtomicReference<>(State.INITIAL);

        private Path path;

        private final Lock readerLock = new ReentrantLock();
        private FileChannel readerChannel;

        private Throwable upstreamError;

        LiveUploadObserver(ByteBodyFactory bbf) {
            var streamingBody = bbf.createStreamingBody(BodySizeLimits.UNLIMITED, this);
            sharedBuffer = streamingBody.sharedBuffer();
            rootBody = streamingBody.rootBody();
            readBufferFactory = bbf.readBufferFactory();
        }

        void receivedNoFile(ReadBuffer rb) {
            State st = state.updateAndGet(s -> s.addForwarded(rb.readable()).addLength(rb.readable()));

            if (st.allowDiscard) {
                rb.close();
            } else {
                sharedBuffer.add(rb);
            }
        }

        private boolean receivedWithFileTryOtherThread(int bytesReceived) {
            while (true) {
                State s = state.get();
                if (s.allowDiscard) {
                    return true;
                }
                if (s.canForwardImmediately()) {
                    return false;
                }
                if (state.compareAndSet(s, s.addLength(bytesReceived))) {
                    // another thread will read the data from disk once it's ready to forward.
                    return true;
                }
            }
        }

        void receivedWithFile(ReadBuffer rb) {
            int n = rb.readable();

            if (receivedWithFileTryOtherThread(n)) {
                rb.close();
                return;
            }

            sharedBuffer.add(rb);
            state.updateAndGet(s -> s.addLength(n).addForwarded(n));
        }

        void upstreamComplete(Throwable error) {
            this.upstreamError = error;
            State s = state.updateAndGet(State::withComplete);
            if (s.canForwardImmediately()) {
                forwardComplete();
            }
        }

        private void forwardComplete() {
            // todo: close file channel
            if (upstreamError == null) {
                sharedBuffer.complete();
            } else {
                sharedBuffer.error(upstreamError);
            }
        }

        @Override
        public void start() {
            onBytesConsumed(1024);
        }

        @Override
        public void onBytesConsumed(long bytesConsumed) {
            State upd = state.updateAndGet(s -> s.addConsumed(bytesConsumed));
            if (!upd.canReplay()) {
                return;
            }

            // todo: could be avoided by detecting whether another thread is already in the loop below
            // asynchronously replay some data from the file.
            diskWriteExecutor.execute(() -> {
                readerLock.lock();
                try {
                    State st = state.get();
                    // loop until we can't replay any further
                    while (true) {
                        if (!st.canReplay()) {
                            break;
                        }
                        if (st.replayable() <= 0) {
                            assert st.complete;
                            forwardComplete();
                            break;
                        }

                        if (readerChannel == null) {
                            readerChannel = FileChannel.open(path, StandardOpenOption.READ);
                        }

                        readerChannel.position(st.forwarded);
                        int toReplay = Math.toIntExact(Math.min(st.replayable(), 128 * 1024));

                        ReadBuffer data = readBufferFactory.copyOf(readerChannel, toReplay);
                        if (data == null) {
                            throw new IOException("Hit end-of-file at " + st.forwarded + " even though file size was supposed to be " + st.length);
                        }

                        int actual = data.readable();
                        sharedBuffer.add(data);

                        st = state.updateAndGet(s -> s.addForwarded(actual));
                    }
                } catch (IOException e) {
                    allowDiscard();
                    sharedBuffer.error(e);
                } finally {
                    readerLock.unlock();
                }
            });
        }

        @Override
        public void allowDiscard() {
            state.getAndUpdate(State::withAllowDiscard);
            diskWriteExecutor.execute(() -> {
                readerLock.lock();
                try {
                    if (readerChannel != null) {
                        try {
                            readerChannel.close();
                        } catch (IOException e) {
                            LOG.debug("Failed to close reader channel", e);
                        }
                        readerChannel = null;
                    }
                } finally {
                    readerLock.unlock();
                }
            });
        }

        private record State(
            long forwarded,
            long consumed,
            long length,
            boolean allowDiscard,
            boolean complete
        ) {
            static final State INITIAL = new State(0, 0, 0, false, false);

            long replayable() {
                return Math.min(length - forwarded, consumed - length);
            }

            boolean canForwardImmediately() {
                return canForward() && replayable() <= 0;
            }

            boolean canReplay() {
                return canForward() && (replayable() > 0 || complete);
            }

            boolean canForward() {
                return consumed >= length && !allowDiscard;
            }

            State addLength(int n) {
                return new State(forwarded, consumed, length + n, allowDiscard, complete);
            }

            State addForwarded(int n) {
                return new State(forwarded + n, consumed, length, allowDiscard, complete);
            }

            private static long addClamp(long a, long b) {
                long sum = a + b;
                if (sum < a) {
                    sum = Long.MAX_VALUE;
                }
                return sum;
            }

            State addConsumed(long n) {
                return new State(forwarded, addClamp(consumed, n), length, allowDiscard, complete);
            }

            State withAllowDiscard() {
                return new State(forwarded, consumed, length, true, complete);
            }

            State withComplete() {
                return new State(forwarded, consumed, length, allowDiscard, true);
            }
        }
    }

    private record PathAndStream(Path path, OutputStream out) implements Closeable {
        @Override
        public void close() throws IOException {
            try {
                out.close();
            } catch (IOException e) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    e.addSuppressed(ex);
                }
                throw e;
            }
            Files.deleteIfExists(path);
        }
    }
}
