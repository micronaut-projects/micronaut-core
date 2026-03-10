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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.io.file.TemporaryFileResource;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.exceptions.HttpStatusException;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Global utility class for form data access.
 *
 * @author Jonas Konrad
 * @since 5.0.0
 */
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

    public Executor getDiskWriteExecutor() {
        return diskWriteExecutor;
    }

    /**
     * Get the completer for the given request, if it has been created by
     * {@link #getOrCreateCompleter(HttpRequest)}.
     *
     * @param request The request
     * @return The completer
     */
    @Nullable
    public static FormRouteCompleter getCompleterOrNull(HttpRequest<?> request) {
        return request.getAttribute(COMPLETER, FormRouteCompleter.class).orElse(null);
    }

    /**
     * Create the completer for the given request if necessary.
     *
     * @param request The request
     * @return The completer
     */
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

    /**
     * Asynchronously buffer the given field. If the field is determined to be a file upload, data
     * may be buffered to disk.
     *
     * @param request   The request this field came in on
     * @param formField The field
     * @return A flow that completes when the full field has been buffered
     */
    public ExecutionFlow<? extends CompletedPart> completePart(FormCapableHttpRequest<?> request, RawFormField formField) {
        if (formField.metadata().fileName() == null) {
            return completeAttribute(request, formField);
        } else {
            return completeFileUpload(request, formField);
        }
    }

    /**
     * Asynchronously buffer the given attribute.
     *
     * @param request   The request this field came in on
     * @param formField The field
     * @return A flow that completes when the full field has been buffered
     */
    public ExecutionFlow<CompletedAttribute> completeAttribute(FormCapableHttpRequest<?> request, RawFormField formField) {
        return InternalByteBody.bufferFlow(formField.byteBody()).map(av -> {
            CompletedAttribute attr = CompletedAttribute.create(formField.metadata(), av.toReadBuffer());
            request.addDisposalResource(attr::close);
            return attr;
        });
    }

    /**
     * Buffer a streamed {@link RawFormField} into a {@link CompletedFileUpload}. May save data to
     * disk, if configured.
     *
     * @param request   Optional request, used to get at the ReadBufferFactory
     * @param formField The form field to stream
     * @return The flow with the uploaded file
     */
    public ExecutionFlow<CompletedFileUpload> completeFileUpload(FormCapableHttpRequest<?> request, RawFormField formField) {
        if (formField.metadata().fileName() == null) {
            formField.close();
            return ExecutionFlow.error(new HttpStatusException(HttpStatus.BAD_REQUEST, "Field [" + formField.metadata().name() + "] was expected to be a file upload, but is missing a file name"));
        }
        ToDiskSubscriber tds = new ToDiskSubscriber(formField.metadata(), request.byteBodyFactory().readBufferFactory());
        Flux.from(formField.byteBody().toReadBufferPublisher()).subscribe(tds);
        request.addDisposalResource(tds::cleanup);
        return tds.result;
    }

    /**
     * Create a new {@link StreamingFileUpload} from the given raw data.
     *
     * @param formField The field
     * @return The streaming upload
     */
    public StreamingFileUpload streamFileUpload(RawFormField formField) {
        return new StreamingFileUpload(formField, diskWriteExecutor);
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
            return new PathAndStream(new TemporaryFileResource(tmp), out);
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
        private static final Object CLOSED_SENTINEL = new Object();

        private final FormFieldMetadata metadata;
        private final ReadBufferFactory bufferFactory;

        private final DelayedExecutionFlow<CompletedFileUpload> result = DelayedExecutionFlow.create();
        private boolean completed;

        @Nullable
        private Subscription subscription;
        private long total = 0;
        @Nullable
        private List<ReadBuffer> memory = new ArrayList<>();
        @Nullable
        private CompletableFuture<PathAndStream> file;
        @Nullable
        private CompletableFuture<PathAndStream> latestPieceWritten;

        /**
         * Contains the current high-level resource that we will close at the end of the request
         * lifecycle.
         *
         * <ol>
         *     <li>Starts with {@code null} while we're still in memory.</li>
         *     <li>When we move to disk, this changes to a {@link TemporaryFileResource}.</li>
         *     <li>When we're done uploading, this becomes the {@link CompletedFileUpload}.</li>
         *     <li>When the request lifecyle ends, this becomes {@link #CLOSED_SENTINEL}.</li>
         * </ol>
         */
        private final AtomicReference<@org.jspecify.annotations.Nullable Object> closeResource = new AtomicReference<>();

        ToDiskSubscriber(FormFieldMetadata metadata, ReadBufferFactory bufferFactory) {
            this.metadata = metadata;
            this.bufferFactory = bufferFactory;
        }

        @Override
        public void onSubscribe(Subscription s) {
            this.subscription = s;
            s.request(1);
        }

        private RuntimeException concurrentClose(@org.jspecify.annotations.Nullable Closeable cl) {
            RuntimeException e = new IllegalStateException("Request terminated with upload in progress");
            if (cl != null) {
                try {
                    cl.close();
                } catch (IOException ex) {
                    e.addSuppressed(ex);
                }
            }
            return e;
        }

        @Override
        public void onNext(ReadBuffer buffer) {
            total += buffer.readable();
            var mc = configuration.getMultipart();
            // check size
            if (total > mc.getMaxFileSize()) {
                buffer.close();
                onError(new ContentLengthExceededException("The part named [" + metadata.name() + "] exceeds the maximum allowed content length [" + mc.getMaxFileSize() + "]"));
                return;
            }
            if (file == null) {
                // do we need to transfer to disk?
                if (mc.isDisk() || (mc.isMixed() && total > mc.getThreshold())) {
                    List<ReadBuffer> memory = Objects.requireNonNull(this.memory);
                    this.memory = null;
                    // transfer asynchronously
                    file = CompletableFuture.supplyAsync(() -> {
                        PathAndStream ps = moveToDisk(memory);
                        if (!closeResource.compareAndSet(null, ps.path)) {
                            throw concurrentClose(ps);
                        }
                        return ps;
                    }, diskWriteExecutor);
                    latestPieceWritten = file;
                } else {
                    // no transfer, just save to memory
                    Objects.requireNonNull(memory).add(buffer);
                    Objects.requireNonNull(subscription).request(1);
                    return;
                }
            }
            // might have to wait for disk transfer here
            latestPieceWritten = file.whenCompleteAsync((p, t) -> {
                if (t != null) {
                    // transfer failed, discard this piece also
                    buffer.close();
                    Objects.requireNonNull(subscription).cancel();
                    result.tryCompleteExceptionally(t);
                    return;
                }
                try {
                    buffer.transferTo(p.out);
                    // transfer complete, request the next piece
                    Objects.requireNonNull(subscription).request(1);
                } catch (IOException e) {
                    // transfer of this piece failed
                    Objects.requireNonNull(subscription).cancel();
                    result.tryCompleteExceptionally(e);
                    try {
                        p.close();
                    } catch (IOException ex) {
                        e.addSuppressed(ex);
                    }
                    throw new CompletionException(e);
                } catch (Throwable e) {
                    // transfer of this piece failed
                    Objects.requireNonNull(subscription).cancel();
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
                if (memory != null) {
                    // close memory buffers
                    for (ReadBuffer readBuffer : memory) {
                        readBuffer.close();
                    }
                }
            }
            result.tryCompleteExceptionally(t);
        }

        @Override
        public void onComplete() {
            if (completed) {
                return;
            }
            assert (file == null) == (latestPieceWritten == null);
            if (latestPieceWritten == null) {
                // all in-memory
                CompletedFileUpload cfu = CompletedFileUpload.ofMemory(metadata, bufferFactory.compose(Objects.requireNonNull(memory)));
                if (closeResource.compareAndSet(null, cfu)) {
                    result.complete(cfu);
                } else {
                    result.completeExceptionally(concurrentClose(null));
                }
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
                            ps.path.close();
                        } catch (IOException ex) {
                            e.addSuppressed(ex);
                        }
                        result.tryCompleteExceptionally(e);
                        return;
                    }
                    // done!
                    CompletedFileUpload cfu = CompletedFileUpload.ofFile(metadata, ps.path, total);
                    if (!closeResource.compareAndSet(ps.path, cfu)) {
                        result.tryCompleteExceptionally(concurrentClose(cfu));
                    } else if (!result.tryComplete(cfu)) {
                        try {
                            cfu.close();
                        } catch (IOException e) {
                            LOG.debug("Failed to close cancelled CompletedFileUpload", e);
                        }
                    }
                }, diskWriteExecutor);
            }
        }

        void cleanup() {
            Object pr = closeResource.getAndSet(CLOSED_SENTINEL);
            if (pr != null && pr != CLOSED_SENTINEL) {
                if (pr instanceof CompletedPart cp) {
                    cp.closeAsync(diskWriteExecutor);
                } else {
                    TemporaryFileResource resource = (TemporaryFileResource) pr;
                    if (resource.isOpen()) {
                        diskWriteExecutor.execute(() -> {
                            try {
                                resource.close();
                            } catch (IOException e) {
                                LOG.debug("Failed to close uploaded temporary file at end of request", e);
                            }
                        });
                    }
                }
            }
        }
    }

    private record PathAndStream(TemporaryFileResource path,
                                 OutputStream out) implements Closeable {
        @Override
        public void close() throws IOException {
            try {
                out.close();
            } catch (IOException e) {
                try {
                    path.close();
                } catch (IOException ex) {
                    e.addSuppressed(ex);
                }
                throw e;
            }
            path.close();
        }
    }
}
