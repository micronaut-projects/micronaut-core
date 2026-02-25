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

import io.micronaut.core.io.buffer.LeakTracker;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.util.functional.ThrowingSupplier;
import io.micronaut.http.body.CloseableByteBody;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Sinks;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.StampedLock;

/**
 * A form file upload that is being streamed to the server. Unlike {@link CompletedFileUpload},
 * this class is never backed by a file. It exerts backpressure on the HTTP connection: If you read
 * slowly or not at all, the upload will slow down on the client.
 * <p>
 * This object is closed when the request terminates. If you wish to use the data for longer, you
 * need to call one of the consumption methods before that happens.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public final class StreamingFileUpload implements Closeable {
    private static final LeakTracker.Factory<StreamingFileUpload> TRACKER_FACTORY = LeakTracker.Factory.forClass(StreamingFileUpload.class);

    @Nullable
    private final LeakTracker<StreamingFileUpload> tracker = TRACKER_FACTORY.track(this);

    private final RawFormField field;
    private final Executor ioExecutor;

    public StreamingFileUpload(
        RawFormField field,
        Executor ioExecutor
    ) {
        this.field = field;
        this.ioExecutor = ioExecutor;
    }

    /**
     * Get the user-supplied metadata for this form field.
     *
     * @return The metadata
     */
    public FormFieldMetadata metadata() {
        return field.metadata();
    }

    /**
     * Stream the data as a {@link CloseableByteBody} as it comes in. This method may only be
     * called once. The returned body must be closed by the caller.
     *
     * @return The streaming data
     */
    public CloseableByteBody streamingBody() {
        CloseableByteBody bb = field.byteBody().move();
        close();
        return bb;
    }

    /**
     * Get the final size of the upload, if given by the user.
     *
     * @return The final upload size
     */
    public OptionalLong getDefinedSize() {
        return field.byteBody().expectedLength();
    }

    /**
     * Get the name of the form field.
     *
     * @return The form field name
     * @see FormFieldMetadata#name()
     */
    public String getName() {
        String name = metadata().name();
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
    public String getFilename() {
        String name = metadata().fileName();
        if (name == null) {
            throw new IllegalStateException("File name not specified");
        }
        return name;
    }

    /**
     * Close this form field, deleting any associated resources and files. If you called
     * {@link #streamingBody()} before, the returned object will still function.
     */
    @Override
    public void close() {
        field.close();
        if (tracker != null) {
            tracker.close(this);
        }
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
        return transferTo(() -> Files.newOutputStream(destination));
    }

    /**
     * <p>A convenience method to write this uploaded item the provided output stream.</p>
     *
     * @param outputStream the destination to which the stream will be written.
     * @return A {@link Publisher} that completes when the transfer is done
     * @since 3.1.0
     */
    public Publisher<?> transferTo(OutputStream outputStream) {
        return transferTo(() -> outputStream);
    }

    private Publisher<?> transferTo(ThrowingSupplier<OutputStream, IOException> supplier) {
        Sinks.One<Object> sink = Sinks.one();
        field.byteBody().toReadBufferPublisher().subscribe(new Subscriber<>() {
            // lock for subscription and outputStream. Used to make sure onSubscribe completes
            // before onError/onComplete processing.
            final StampedLock outputLock = new StampedLock();
            @Nullable Subscription subscription;
            @Nullable OutputStream outputStream;

            @Override
            public void onSubscribe(Subscription s) {
                this.subscription = s;
                // if there is an onComplete immediately after onSubscribe, this lock delays it
                // until the onSubscribe execute is done.
                long stamp = outputLock.tryWriteLock();
                if (stamp == 0) {
                    throw new IllegalStateException("Already locked?");
                }
                ioExecutor.execute(() -> {
                    boolean success = false;
                    try {
                        outputStream = supplier.get();
                        success = true;
                    } catch (Exception e) {
                        s.cancel();
                        sink.tryEmitError(e);
                    } finally {
                        outputLock.unlockWrite(stamp);
                    }
                    if (success) {
                        s.request(1);
                    }
                });
            }

            @Override
            public void onNext(ReadBuffer readBuffer) {
                // if there is an onComplete immediately after onNext, this lock delays it until
                // the onNext execute is done.
                long stamp = outputLock.tryWriteLock();
                if (stamp == 0) {
                    throw new IllegalStateException("Already locked?");
                }
                ioExecutor.execute(() -> {
                    boolean success = false;
                    try (readBuffer) {
                        readBuffer.transferTo(Objects.requireNonNull(outputStream));
                        success = true;
                    } catch (Exception e) {
                        try {
                            Objects.requireNonNull(outputStream).close();
                        } catch (IOException ex) {
                            e.addSuppressed(ex);
                        }
                        Objects.requireNonNull(subscription).cancel();
                        sink.tryEmitError(e);
                    } finally {
                        outputLock.unlock(stamp);
                    }
                    if (success) {
                        Objects.requireNonNull(subscription).request(1);
                    }
                });
            }

            @Override
            public void onError(Throwable t) {
                ioExecutor.execute(() -> {
                    long stamp = outputLock.writeLock();
                    try {
                        Objects.requireNonNull(outputStream).close();
                    } catch (IOException ex) {
                        t.addSuppressed(ex);
                    } finally {
                        outputLock.unlockWrite(stamp);
                    }
                    sink.tryEmitError(t);
                });
            }

            @Override
            public void onComplete() {
                ioExecutor.execute(() -> {
                    long stamp = outputLock.writeLock();
                    try {
                        Objects.requireNonNull(outputStream).close();
                        sink.tryEmitEmpty();
                    } catch (IOException ex) {
                        sink.tryEmitError(ex);
                    } finally {
                        outputLock.unlockWrite(stamp);
                    }
                });
            }
        });
        close();
        return sink.asMono();
    }

    /**
     * Create an {@link InputStream} that reads this file. The returned stream must be closed after
     * use. The stream may block when data isn't yet available.
     *
     * @return An {@link InputStream} that reads this file's contents
     * @since 4.2.0
     */
    public InputStream asInputStream() {
        InputStream s = field.byteBody().toInputStream();
        close();
        return s;
    }
}
