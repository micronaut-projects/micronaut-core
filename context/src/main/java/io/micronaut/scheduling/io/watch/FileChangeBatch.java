/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.scheduling.io.watch;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.scheduling.io.watch.event.WatchEventType;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * The changes under one registration's root that happened within one quiet period.
 *
 * @param root The absolute root of the registration the batch is delivered to
 * @param changes The changes, in the order they were first observed, with absolute paths
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public record FileChangeBatch(Path root, List<FileChange> changes) {

    /**
     * Validating constructor.
     *
     * @param root The absolute root
     * @param changes The changes
     */
    public FileChangeBatch {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(changes, "changes");
        changes = List.copyOf(changes);
    }

    /**
     * Whether any change in the batch is of the given type.
     *
     * @param type The type
     * @return True if present
     */
    public boolean contains(WatchEventType type) {
        return changes.stream().anyMatch(change -> change.type() == type);
    }

    /**
     * The changed paths.
     *
     * @return The absolute paths, in batch order
     */
    public List<Path> paths() {
        return changes.stream().map(FileChange::path).toList();
    }
}
