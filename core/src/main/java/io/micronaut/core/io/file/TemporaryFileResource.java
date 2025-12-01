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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.LeakTracker;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

// TODO: docs
public final class TemporaryFileResource implements Closeable {
    private static final LeakTracker.Factory<TemporaryFileResource> TRACKER_FACTORY = LeakTracker.Factory.forClass(TemporaryFileResource.class);

    private final LeakTracker<TemporaryFileResource> tracker = TRACKER_FACTORY.track(this);
    private final AtomicReference<Path> path;

    public TemporaryFileResource(Path path) {
        this.path = new AtomicReference<>(Objects.requireNonNull(path, "path"));
    }

    @NonNull
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

    @NonNull
    private Path claimPath() {
        Path p = claimPathOrNull();
        if (p == null) {
            throw new IllegalStateException("Temporary file resource has been closed or moved");
        }
        return p;
    }

    @NonNull
    public TemporaryFileResource moveResource() {
        return new TemporaryFileResource(claimPath());
    }

    public void moveFile(Path destination) throws IOException {
        Path p = claimPath();
        try {
            Files.move(p, destination);
        } catch (IOException e) {
            try {
                Files.delete(p);
            } catch (IOException ex) {
                e.addSuppressed(ex);
            }
            throw e;
        }
    }

    public boolean isOpen() {
        return path.get() != null;
    }

    @Override
    public void close() throws IOException {
        Path p = claimPathOrNull();
        if (p != null) {
            Files.delete(p);
        }
    }
}
