/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.multipart;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.LeakTracker;
import io.micronaut.http.body.CloseableByteBody;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;
import java.util.concurrent.Executor;

/**
 * <p>Represents a part of a {@link io.micronaut.http.MediaType#MULTIPART_FORM_DATA} request.</p>
 *
 * <p>The {@code StreamingFileUpload} may be incomplete when first received, in which case the consumer can subscribe
 * to the file upload to process the data a chunk at a time.</p>
 *
 * <p>The {@link #transferTo(String)} method can be used whether the upload is complete or not. If it is not complete
 * the framework will automatically subscribe to the upload and transfer the data chunk by chunk in a non-blocking
 * manner</p>
 *
 * <p>All I/O operation return a {@link Publisher} that runs on the the configured I/O
 * {@link java.util.concurrent.ExecutorService}</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public final class StreamingFileUpload implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(StreamingFileUpload.class);
    private static final LeakTracker.Factory<StreamingFileUpload> TRACKER_FACTORY = LeakTracker.Factory.forClass(StreamingFileUpload.class);

    private final LeakTracker<StreamingFileUpload> tracker = TRACKER_FACTORY.track(this);

    @NonNull
    private final FormFieldMetadata metadata;
    @Nullable
    private ExecutionFlow<CompletedFileUpload> completedFileUpload;
    @NonNull
    private final CloseableByteBody streamingByteBody;
    @NonNull
    private final Executor ioExecutor;

    public StreamingFileUpload(
        @NonNull FormFieldMetadata metadata,
        @NonNull ExecutionFlow<CompletedFileUpload> completedFileUpload,
        @NonNull CloseableByteBody streamingByteBody,
        @NonNull Executor ioExecutor
    ) {
        this.metadata = metadata;
        this.completedFileUpload = completedFileUpload;
        this.streamingByteBody = streamingByteBody;
        this.ioExecutor = ioExecutor;
    }

    @NonNull
    public CloseableByteBody streamingByteBody() {
        return streamingByteBody.move();
    }

    @NonNull
    public OptionalLong getDefinedSize() {
        return streamingByteBody.expectedLength();
    }

    @NonNull
    public String getName() {
        String name = metadata.name();
        if (name == null) {
            throw new IllegalStateException("Name not specified");
        }
        return name;
    }

    @NonNull
    public String getFilename() {
        String name = metadata.fileName();
        if (name == null) {
            throw new IllegalStateException("File name not specified");
        }
        return name;
    }

    private ExecutionFlow<CompletedFileUpload> claimCompleted() {
        ExecutionFlow<CompletedFileUpload> cfu = completedFileUpload;
        completedFileUpload = null;
        return cfu;
    }

    @Override
    public void close() {
        ExecutionFlow<CompletedFileUpload> cfu = claimCompleted();
        if (cfu != null) {
            cfu.cancel(cf -> {
                try {
                    cf.close();
                } catch (IOException e) {
                    LOG.debug("Failed to close completed upload");
                }
            });
        }
        streamingByteBody.close();
        if (tracker != null) {
            tracker.close(this);
        }
    }

    /**
     * <p>A convenience method to write this uploaded item to disk.</p>
     *
     * @param destination the destination of the file to which the stream will be written.
     * @return A {@link Publisher} that outputs whether the transfer was successful
     */
    public Publisher<?> transferTo(File destination) {
        return transferTo(destination.toPath());
    }

    /**
     * <p>A convenience method to write this uploaded item to disk.</p>
     *
     * @param destination the destination of the file to which the stream will be written.
     * @return A {@link Publisher} that outputs whether the transfer was successful
     */
    public Publisher<?> transferTo(Path destination) {
        Sinks.One<?> sink = Sinks.one();
        streamingByteBody.close();
        claimCompleted().onComplete((cf, t) -> {
            if (t != null) {
                sink.tryEmitError(t);
            } else {
                if (cf instanceof CompletedFileUpload.File cff) {
                    try {
                        Files.move(cff.getPath(), destination);
                    } catch (IOException e) {
                        try {
                            Files.deleteIfExists(cff.getPath());
                        } catch (IOException ex) {
                            e.addSuppressed(ex);
                        }
                        sink.tryEmitError(e);
                    } finally {
                        cff.closeTracker();
                    }
                }
                sink.tryEmitEmpty();
            }
        });
        return sink.asMono();
    }

    /**
     * <p>A convenience method to write this uploaded item the provided output stream.</p>
     *
     * @param outputStream the destination to which the stream will be written.
     * @return A {@link Publisher} that outputs whether the transfer was successful
     * @since 3.1.0
     */
    public Publisher<?> transferTo(OutputStream outputStream) {
        return Flux.from(streamingByteBody.toReadBufferPublisher())
            .publishOn(Schedulers.fromExecutor(ioExecutor))
            .doOnNext(rb -> {
                try {
                    rb.transferTo(outputStream);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            })
            .ignoreElements();
    }

    /**
     * Create an {@link InputStream} that reads this file. The returned stream must be closed after
     * use. The stream may block when data isn't yet available.
     *
     * @return An {@link InputStream} that reads this file's contents
     * @since 4.2.0
     */
    @NonNull
    public InputStream asInputStream() {
        return streamingByteBody.toInputStream();
    }
}
