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
package io.micronaut.core.io.file;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.LeakTracker;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Closeable resource class representing a temporary file. When this resource is closed, the file
 * is deleted.
 *
 * @since 5.0.0
 * @author Jonas Konrad
 */
@Internal
public final class TemporaryFileResource implements Closeable {
    private static final LeakTracker.Factory<TemporaryFileResource> TRACKER_FACTORY = LeakTracker.Factory.forClass(TemporaryFileResource.class);

    @Nullable
    private final LeakTracker<TemporaryFileResource> tracker = TRACKER_FACTORY.track(this);
    private final AtomicReference<@Nullable Path> path;

    /**
     * Create a new file resource. Ownership of the path is transferred to the resource.
     *
     * @param path The path
     */
    public TemporaryFileResource(Path path) {
        this.path = new AtomicReference<>(Objects.requireNonNull(path, "path"));
    }

    /**
     * Get the path.
     *
     * @return The path
     * @throws IllegalStateException if this resource is closed or has been moved
     */
    public Path getPath() {
        Path path = this.path.get();
        if (path == null) {
            throw new IllegalStateException("Temporary file resource has been closed or moved");
        }
        return path;
    }

    @Nullable
    private Path claimPathOrNull() {
        Path p = path.getAndSet(null);
        if (p == null) {
            return null;
        }
        if (tracker != null) {
            tracker.close(this);
        }
        return p;
    }

    private Path claimPath() {
        Path p = claimPathOrNull();
        if (p == null) {
            throw new IllegalStateException("Temporary file resource has been closed or moved");
        }
        return p;
    }

    /**
     * Move this <i>resource</i>. The returned resource will have ownership of the path, and the
     * original resource will lose ownership, meaning it is essentially closed. No actual file
     * system operation is done.
     *
     * @return The new resource managing this path
     * @throws IllegalStateException if this resource is already closed or has been moved
     */
    public TemporaryFileResource moveResource() {
        return new TemporaryFileResource(claimPath());
    }

    /**
     * Move this <i>file</i> to a new location. This resource loses ownership of the file, you'll
     * have to manage it from now on.
     *
     * @param destination The destination to move to
     * @throws IOException If the file cannot be moved. Note that the file will be deleted in this
     * case, the resource still becomes invalid
     */
    public void moveFile(Path destination) throws IOException {
        Path p = claimPath();
        try {
            Files.move(p, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.delete(p);
            } catch (IOException ex) {
                e.addSuppressed(ex);
            }
            throw e;
        }
    }

    /**
     * Check whether this resource is still open and has ownership of the path.
     *
     * @return {@code true} if this resource is still open
     */
    public boolean isOpen() {
        return path.get() != null;
    }

    /**
     * Close this resource, deleting the underlying file. If this resource is already closed or has
     * been moved, this method does nothing.
     *
     * @throws IOException Failure deleting the file
     */
    @Override
    public void close() throws IOException {
        Path p = claimPathOrNull();
        if (p != null) {
            Files.delete(p);
        }
    }
}
