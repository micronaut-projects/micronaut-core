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
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
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
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.Executor;

/**
 * A form file upload that is being streamed to the server. Like {@link CompletedFileUpload}, this
 * object may be backed by memory or by a file, depending on server configuration. However, when
 * using {@link StreamingFileUpload} as a controller parameter, the controller will run
 * immediately, even if the file upload is still in progress. This allows you to start streaming
 * the file upload before it's done.
 * <p>
 * Unlike a normal {@link Publisher}, a {@link StreamingFileUpload} <i>does not</i> exert
 * backpressure on the upload. That means that even if you consume the data very slowly or not at
 * all, the upload will not be affected. Data will be buffered to disk until it's used.
 * <p>
 * A {@link StreamingFileUpload} <b>must be closed</b> after use to clean up any remaining files
 * and resources.
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

    /**
     * Get the user-supplied metadata for this form field.
     *
     * @return The metadata
     */
    public @NonNull FormFieldMetadata metadata() {
        return metadata;
    }

    /**
     * Stream the data as a {@link CloseableByteBody} as it comes in. This method may only be
     * called once. The returned body must be closed by the caller.
     *
     * @return The streaming data
     */
    @NonNull
    public CloseableByteBody streamingBody() {
        return streamingByteBody.move();
    }

    /**
     * Get the final size of the upload, if given by the user.
     *
     * @return The final upload size
     */
    @NonNull
    public OptionalLong getDefinedSize() {
        return streamingByteBody.expectedLength();
    }

    /**
     * Get the name of the form field.
     *
     * @return The form field name
     * @see FormFieldMetadata#name()
     */
    @NonNull
    public String getName() {
        String name = metadata.name();
        if (name == null) {
            throw new IllegalStateException("Name not specified");
        }
        return name;
    }

    /**
     * Get the user-specified file name of the uploaded file.
     *
     * @return The file name
     * @see FormFieldMetadata#fileName()
     */
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
        streamingByteBody.close();
        return cfu;
    }

    /**
     * Close this form field, deleting any associated resources and files. If you called
     * {@link #streamingBody()}, {@link #completedFile()} or other methods before, the returned
     * objects will still function.
     */
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
     * Get a publisher that completes when this file is fully uploaded. The caller must take care
     * to close the returned {@link CompletedFileUpload}. This method may only be called once.
     *
     * @return The publisher
     */
    public Publisher<CompletedFileUpload> completedFile() {
        return ReactiveExecutionFlow.toPublisher(Objects.requireNonNull(claimCompleted(), "Already claimed"));
    }

    /**
     * A convenience method to write this uploaded item to disk.
     *
     * @param destination the destination of the file to which the stream will be written.
     * @return A {@link Publisher} that outputs whether the transfer was successful
     */
    public Publisher<?> transferTo(File destination) {
        return transferTo(destination.toPath());
    }

    /**
     * A convenience method to write this uploaded item to disk.
     *
     * @param destination the destination of the file to which the stream will be written.
     * @return A {@link Publisher} that outputs whether the transfer was successful
     */
    public Publisher<?> transferTo(Path destination) {
        Sinks.One<?> sink = Sinks.one();
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
