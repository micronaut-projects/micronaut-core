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
package io.micronaut.dev.compile;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A directory of sources of one language.
 *
 * @param kind The language
 * @param path The absolute directory
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public record SourceRoot(SourceKind kind, Path path) {

    /**
     * Validating constructor.
     *
     * @param kind The language
     * @param path The directory, made absolute
     */
    public SourceRoot {
        Objects.requireNonNull(kind, "kind");
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    /**
     * Whether a file lies under this root and is a source of its kind.
     *
     * @param file The file
     * @return True if the root owns the file
     */
    public boolean owns(Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        return absolute.startsWith(path) && kind.matches(absolute);
    }

    /**
     * The binary name of the top-level class a source file under this root declares, by its path:
     * {@code src/main/java/com/acme/Foo.java} is {@code com.acme.Foo}.
     *
     * @param file A source file under the root
     * @return The name
     * @throws IllegalArgumentException if the file is not under the root
     */
    public String classNameOf(Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        if (!absolute.startsWith(path)) {
            throw new IllegalArgumentException(file + " is not under " + path);
        }
        String relative = path.relativize(absolute).toString().replace(java.io.File.separatorChar, '.');
        int dot = relative.lastIndexOf('.');
        return dot > 0 ? relative.substring(0, dot) : relative;
    }
}
