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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.io.file.TemporaryFileResource;
import io.micronaut.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Represents a completed part of a multipart request. May be backed by a file or memory depending
 * on server configuration.
 * <p>
 * When used as an argument to an {@link io.micronaut.http.annotation.Controller} instance method, the route
 * is not executed until the part has been fully received. Provides access to metadata about the file as
 * well as the contents.
 * <p>
 * This object is closed when the request terminates. If you wish to use the data for longer, you
 * need to call one of the consumption methods before that happens.
 *
 * @author Zachary Klein
 * @since 1.0.0
 */
public abstract sealed class CompletedFileUpload extends CompletedPart {
    private static final Logger LOG = LoggerFactory.getLogger(CompletedFileUpload.class);

    CompletedFileUpload(FormFieldMetadata metadata) {
        super(metadata);
    }

    @Override
    public abstract CompletedFileUpload moveResource();

    /**
     * Create a new memory-backed file upload. Ownership of the data buffer transfers to the file
     * upload object. Closing the file upload object will close the memory.
     *
     * @param metadata The field metadata
     * @param memory   The field data
     * @return The file upload structure
     */
    public static CompletedFileUpload ofMemory(FormFieldMetadata metadata, ReadBuffer memory) {
        return new Memory(metadata, memory);
    }

    /**
     * Create a new file-backed file upload. Ownership of the file transfers to the file upload
     * object. Closing the file upload object will delete the backing file.
     *
     * @param metadata The field metadata
     * @param path     The backing file
     * @param size     The size of the backing file
     * @return The file upload structure
     */
    @Experimental
    public static CompletedFileUpload ofFile(FormFieldMetadata metadata, TemporaryFileResource path, long size) {
        return new File(metadata, path, size);
    }

    /**
     * Get the content type of the file, if specified.
     *
     * @return The content type
     * @see FormFieldMetadata#mediaType()
     */
    public final Optional<MediaType> getContentType() {
        return Optional.ofNullable(getMetadata().mediaType());
    }

    /**
     * Get the file name provided by the user.
     *
     * @return The file name
     * @see FormFieldMetadata#fileName()
     */
    public final String getFilename() {
        return Objects.requireNonNull(getMetadata().fileName(), "Field name not given");
    }

    /**
     * Transfer this upload to the given file. This operation closes this
     * {@link CompletedFileUpload}. No further operations may be performed.
     *
     * @param destination The target file
     */
    public abstract void transferTo(Path destination) throws IOException;

    static final class Memory extends CompletedFileUpload {
        private final ReadBuffer buffer;
        private final int size;

        Memory(FormFieldMetadata metadata, ReadBuffer buffer) {
            super(metadata);
            this.buffer = buffer;
            this.size = buffer.readable();
        }

        @Override
        public boolean isInMemory() {
            return true;
        }

        @Override
        public void close() {
            closeTracker();
            buffer.close();
        }

        @Override
        public void closeAsync(Executor ioExecutor) {
            close();
        }

        @Override
        public long getSize() {
            return size;
        }

        @Override
        public InputStream getInputStream() {
            return toReadBuffer().toInputStream();
        }

        @Override
        public ReadBuffer toReadBuffer() {
            return buffer.duplicate();
        }

        @Override
        public CompletedFileUpload moveResource() {
            return new Memory(getMetadata(), toReadBuffer());
        }

        @Override
        public void transferTo(Path destination) throws IOException {
            try (OutputStream os = Files.newOutputStream(destination)) {
                // buffer is intentionally consumed to match file-backed impl
                buffer.transferTo(os);
            }
        }
    }

    static final class File extends CompletedFileUpload {
        private final TemporaryFileResource path;
        private final long actualSize;

        File(FormFieldMetadata metadata, TemporaryFileResource path, long actualSize) {
            super(metadata);
            this.path = path;
            this.actualSize = actualSize;
        }

        @Override
        public boolean isInMemory() {
            return false;
        }

        @Override
        public void close() throws IOException {
            if (Schedulers.isInNonBlockingThread()) {
                throw new IllegalStateException("CompletedFileUpload.close called in non-blocking thread. This is a blocking operation (it deletes the file). You may want to annotate your controller with @ExecuteOn(TaskExecutors.BLOCKING).");
            }
            closeTracker();
            path.close();
        }

        @Override
        public void closeAsync(Executor ioExecutor) {
            TemporaryFileResource p = path;
            if (p.isOpen()) {
                closeTracker();
                ioExecutor.execute(() -> {
                    try {
                        p.close();
                    } catch (IOException e) {
                        LOG.debug("Failed to close file upload");
                    }
                });
            }
        }

        @Override
        public long getSize() {
            return actualSize;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            if (Schedulers.isInNonBlockingThread()) {
                throw new IllegalStateException("CompletedFileUpload.getInputStream called in non-blocking thread. This is a blocking operation. You may want to annotate your controller with @ExecuteOn(TaskExecutors.BLOCKING).");
            }
            return Files.newInputStream(path.getPath());
        }

        @Override
        public ReadBuffer toReadBuffer() throws IOException {
            if (Schedulers.isInNonBlockingThread()) {
                throw new IllegalStateException("CompletedFileUpload.toReadBuffer called in non-blocking thread. This is a blocking operation. You may want to annotate your controller with @ExecuteOn(TaskExecutors.BLOCKING).");
            }
            return ReadBufferFactory.getJdkFactory().copyOf(getInputStream());
        }

        @Override
        public CompletedFileUpload moveResource() {
            return new File(getMetadata(), path.moveResource(), actualSize);
        }

        @Override
        public void transferTo(Path destination) throws IOException {
            if (Schedulers.isInNonBlockingThread()) {
                throw new IllegalStateException("CompletedFileUpload.transferTo called in non-blocking thread. This is a blocking operation. You may want to annotate your controller with @ExecuteOn(TaskExecutors.BLOCKING).");
            }
            closeTracker();
            path.moveFile(destination);
        }
    }
}
