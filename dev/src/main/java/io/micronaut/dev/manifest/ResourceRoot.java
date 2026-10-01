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
package io.micronaut.dev.manifest;

import io.micronaut.context.reload.ResourceKind;
import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A directory of resources of one kind.
 *
 * @param kind What the resources are
 * @param path The absolute directory
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public record ResourceRoot(ResourceKind kind, Path path) {

    /**
     * Validating constructor.
     *
     * @param kind The kind
     * @param path The directory, made absolute
     */
    public ResourceRoot {
        Objects.requireNonNull(kind, "kind");
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    /**
     * Whether a file lies under this root.
     *
     * @param file The file
     * @return True if it does
     */
    public boolean owns(Path file) {
        return file.toAbsolutePath().normalize().startsWith(path);
    }
}
