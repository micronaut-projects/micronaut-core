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
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Represents a completed part of a multipart request.
 *
 * @author James Kleeh
 * @since 1.3.0
 */
public abstract sealed class CompletedPart implements Closeable permits CompletedAttribute, CompletedFileUpload {
    private static final LeakTracker.Factory<CompletedPart> TRACKER_FACTORY = LeakTracker.Factory.forClass(CompletedPart.class);

    @Nullable
    private final LeakTracker<CompletedPart> tracker = TRACKER_FACTORY.track(this);

    private final FormFieldMetadata metadata;

    CompletedPart(FormFieldMetadata metadata) {
        this.metadata = metadata;
    }

    /**
     * Get the metadata for this field.
     *
     * @return The metadata
     */
    public final FormFieldMetadata getMetadata() {
        return metadata;
    }

    /**
     * Get the form field name.
     *
     * @return The field name
     * @see FormFieldMetadata#name()
     */
    public final String getName() {
        return Objects.requireNonNull(metadata.name(), "Field name not given");
    }

    final void closeTracker() {
        if (tracker != null) {
            tracker.close(this);
        }
    }

    public abstract boolean isInMemory();

    /**
     * {@link #close()} may be a blocking operation. This method closes this part asynchronously
     * instead, on the given executor, if a blocking operation needs to be performed.
     *
     * @param ioExecutor The executor
     */
    public abstract void closeAsync(Executor ioExecutor);

    /**
     * Get the definite size in bytes of the form field value. Remains accessible after this part
     * is closed.
     *
     * @return The size in bytes
     */
    public abstract long getSize();

    /**
     * Open a new {@link InputStream} that reads this form field. The returned stream <i>must be
     * closed</i>. You must still close this {@link CompletedPart} also.
     * <p>
     * This is a blocking operation.
     *
     * @return A stream
     */
    public abstract InputStream getInputStream() throws IOException;

    /**
     * Read all data of this form field into memory. The returned buffer <i>must be closed</i>
     * after use. You must still close this {@link CompletedPart} also.
     * <p>
     * This is a blocking operation.
     * <p>
     * <b>This operation has no size limit. If the uploaded file is large, calling this method
     * may use a lot of memory.</b>
     *
     * @return The buffered value
     */
    public abstract ReadBuffer toReadBuffer() throws IOException;

    /**
     * Read all data of this form field into memory. You must still close this
     * {@link CompletedPart}.
     * <p>
     * This is a blocking operation.
     * <p>
     * <b>This operation has no size limit. If the uploaded file is large, calling this method
     * may use a lot of memory.</b>
     *
     * @return The buffered value
     */
    public final byte[] getBytes() throws IOException {
        try (ReadBuffer rb = toReadBuffer()) {
            return rb.toArray();
        }
    }

    /**
     * The completed part objects passed to a controller are closed when the associated request
     * ends. If you want to keep them around for longer, you can use this method to create a new
     * {@link CompletedPart} with the same data. You are responsible for closing the new part.
     *
     * @return A new completed part
     */
    public abstract CompletedPart moveResource();
}
