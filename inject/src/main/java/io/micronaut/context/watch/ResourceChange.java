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
package io.micronaut.context.watch;

import io.micronaut.context.reload.ResourceKind;
import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One batch of changes to the resources of a kind. The first batch a watch receives is the
 * {@link #initial() initial} one, listing what is under the roots; later batches name what changed
 * and what went.
 *
 * @param kind The kind of resource root
 * @param roots The roots of the kind, absolute
 * @param changed The files added or modified, absolute; for the initial batch, every file present
 * @param removed The files deleted, absolute
 * @param initial Whether this is the first batch, describing the state rather than a change
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public record ResourceChange(ResourceKind kind, List<Path> roots, List<Path> changed, List<Path> removed, boolean initial) {

    /**
     * Validating constructor.
     *
     * @param kind The kind
     * @param roots The roots
     * @param changed The changed files
     * @param removed The removed files
     * @param initial Whether initial
     */
    public ResourceChange {
        Objects.requireNonNull(kind, "kind");
        roots = List.copyOf(Objects.requireNonNull(roots, "roots"));
        changed = List.copyOf(Objects.requireNonNull(changed, "changed"));
        removed = List.copyOf(Objects.requireNonNull(removed, "removed"));
    }

    /**
     * Whether a file is among the changed or removed ones.
     *
     * @param path The file
     * @return True if it is
     */
    public boolean affects(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        return changed.contains(absolute) || removed.contains(absolute);
    }

    /**
     * Whether a changed or removed file has the given extension.
     *
     * @param extension The extension without the dot, compared ignoring case
     * @return True if one does
     */
    public boolean anyWithExtension(String extension) {
        String suffix = "." + extension.toLowerCase(Locale.ROOT);
        for (List<Path> paths : List.of(changed, removed)) {
            for (Path path : paths) {
                if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(suffix)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * This change narrowed to what a selector selects.
     *
     * @param selector The selector, of this change's kind
     * @return The narrowed change, whose files all match the selector
     */
    public ResourceChange select(ResourceSelector selector) {
        List<Path> selectedChanged = new ArrayList<>();
        for (Path path : changed) {
            if (selector.matches(roots, path)) {
                selectedChanged.add(path);
            }
        }
        List<Path> selectedRemoved = new ArrayList<>();
        for (Path path : removed) {
            if (selector.matches(roots, path)) {
                selectedRemoved.add(path);
            }
        }
        return new ResourceChange(kind, roots, selectedChanged, selectedRemoved, initial);
    }

    /**
     * @return Whether the change names no file
     */
    public boolean isEmpty() {
        return changed.isEmpty() && removed.isEmpty();
    }
}
