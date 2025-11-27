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
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.MediaType;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a completed part of a multipart request.
 * <p>
 * When used as an argument to an {@link io.micronaut.http.annotation.Controller} instance method, the route
 * is not executed until the part has been fully received. Provides access to metadata about the file as
 * well as the contents.
 *
 * @author Zachary Klein
 * @since 1.0.0
 */
public abstract sealed class CompletedFileUpload extends CompletedPart implements Closeable {
    CompletedFileUpload(@NonNull FormFieldMetadata metadata) {
        super(metadata);
    }

    // TODO: docs
    @NonNull
    public static CompletedFileUpload ofMemory(@NonNull FormFieldMetadata metadata, @NonNull ReadBuffer memory) {
        return new Memory(metadata, memory);
    }

    @NonNull
    public static CompletedFileUpload ofFile(@NonNull FormFieldMetadata metadata, @NonNull Path path, long size) {
        return new File(metadata, path, size);
    }

    public final Optional<MediaType> getContentType() {
        return Optional.ofNullable(getMetadata().mediaType());
    }

    public final String getFilename() {
        return Objects.requireNonNull(getMetadata().fileName(), "Field name not given");
    }

    static final class Memory extends CompletedFileUpload {
        @NonNull
        private final ReadBuffer buffer;

        public Memory(@NonNull FormFieldMetadata metadata, @NonNull ReadBuffer buffer) {
            super(metadata);
            this.buffer = buffer;
        }

        @Override
        public void close() {
            closeTracker();
            buffer.close();
        }

        @Override
        public long getSize() {
            return buffer.readable();
        }

        @Override
        public InputStream getInputStream() {
            return buffer.toInputStream();
        }

        @Override
        public ReadBuffer toReadBuffer() {
            try (this) {
                return buffer.move();
            }
        }
    }

    static final class File extends CompletedFileUpload {
        @NonNull
        private final Path path;
        private final long actualSize;

        public File(@NonNull FormFieldMetadata metadata, @NonNull Path path, long actualSize) {
            super(metadata);
            this.path = path;
            this.actualSize = actualSize;
        }

        public @NonNull Path getPath() {
            return path;
        }

        @Override
        public void close() throws IOException {
            closeTracker();
            Files.delete(path);
        }

        @Override
        public long getSize() {
            return actualSize;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public ReadBuffer toReadBuffer() throws IOException {
            try (this) {
                return ReadBufferFactory.getJdkFactory().copyOf(getInputStream());
            }
        }
    }
}
