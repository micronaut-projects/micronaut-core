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

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The languages a source root holds.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public enum SourceKind {
    /**
     * Java sources.
     */
    JAVA("java"),
    /**
     * Kotlin sources.
     */
    KOTLIN("kt", "kts"),
    /**
     * Groovy sources.
     */
    GROOVY("groovy"),
    /**
     * Python sources.
     */
    PYTHON("py");

    private final Set<String> extensions;

    SourceKind(String... extensions) {
        this.extensions = Set.of(extensions);
    }

    /**
     * @return The file extensions, without the dot
     */
    public Set<String> extensions() {
        return extensions;
    }

    /**
     * Whether a file is a source of this kind, by its extension.
     *
     * @param file The file
     * @return True if it is
     */
    public boolean matches(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && extensions.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /**
     * The kind named in a manifest, ignoring case.
     *
     * @param name The name, such as {@code java}
     * @return The kind, empty if unknown
     */
    public static Optional<SourceKind> of(String name) {
        for (SourceKind kind : values()) {
            if (kind.name().equalsIgnoreCase(name)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
