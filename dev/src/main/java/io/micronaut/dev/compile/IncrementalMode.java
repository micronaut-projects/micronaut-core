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

import java.util.Locale;
import java.util.Optional;

/**
 * What an incremental compilation of a language with dynamic relationships between its sources recompiles, which
 * only the Python compiler distinguishes.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public enum IncrementalMode {
    /**
     * Every source of the language, when the static analysis cannot prove that the change is isolated.
     */
    CONSERVATIVE,
    /**
     * The sources the statically discovered dependencies select, whatever the analysis cannot see. A change to the
     * body of a function does not reach the dependents of its module either.
     */
    OPTIMISTIC;

    /**
     * The mode named in a manifest: {@code conservative} or {@code optimistic}, ignoring case.
     *
     * @param name The name
     * @return The mode, empty if unknown
     */
    public static Optional<IncrementalMode> of(String name) {
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        for (IncrementalMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}
