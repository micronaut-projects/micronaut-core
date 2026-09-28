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

import java.util.Optional;

/**
 * Who compiles the sources of a language.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public enum CompileMode {
    /**
     * A {@link SourceCompiler} inside the development JVM.
     */
    EMBEDDED,
    /**
     * The build tool. The launcher waits for its trigger and then reads the output directories.
     */
    BUILD_TOOL;

    /**
     * The mode named in a manifest: {@code embedded} or {@code build-tool}, ignoring case.
     *
     * @param name The name
     * @return The mode, empty if unknown
     */
    public static Optional<CompileMode> of(String name) {
        String normalized = name.trim().replace('-', '_').toUpperCase(java.util.Locale.ROOT);
        for (CompileMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}
