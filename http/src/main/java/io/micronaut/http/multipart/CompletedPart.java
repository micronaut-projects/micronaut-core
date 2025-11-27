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
import io.micronaut.core.io.buffer.LeakTracker;
import io.micronaut.core.io.buffer.ReadBuffer;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Represents a completed part of a multipart request.
 *
 * @author James Kleeh
 * @since 1.3.0
 */
public abstract sealed class CompletedPart implements Closeable permits CompletedAttribute, CompletedFileUpload {
    private static final LeakTracker.Factory<CompletedPart> TRACKER_FACTORY = LeakTracker.Factory.forClass(CompletedPart.class);

    private final LeakTracker<CompletedPart> tracker = TRACKER_FACTORY.track(this);

    @NonNull
    private final FormFieldMetadata metadata;

    CompletedPart(@NonNull FormFieldMetadata metadata) {
        this.metadata = metadata;
    }

    @NonNull
    public final FormFieldMetadata getMetadata() {
        return metadata;
    }

    @NonNull
    public final String getName() {
        return Objects.requireNonNull(metadata.name(), "Field name not given");
    }

    final void closeTracker() {
        if (tracker != null) {
            tracker.close(this);
        }
    }

    public abstract long getSize();

    public abstract InputStream getInputStream() throws IOException;

    public abstract ReadBuffer toReadBuffer() throws IOException;

    public final byte[] getBytes() throws IOException {
        try (ReadBuffer rb = toReadBuffer()) {
            return rb.toArray();
        }
    }
}
