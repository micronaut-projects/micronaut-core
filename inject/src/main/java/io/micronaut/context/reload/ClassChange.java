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
package io.micronaut.context.reload;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/**
 * One class the application changed.
 *
 * @param className The binary name of the class
 * @param kind Whether the class was added, modified or removed
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public record ClassChange(String className, Kind kind) {

    /**
     * Validating constructor.
     *
     * @param className The binary name
     * @param kind The kind of change
     */
    public ClassChange {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(kind, "kind");
    }

    /**
     * The kind of change.
     */
    public enum Kind {
        /**
         * The class did not exist in the retired generation.
         */
        ADDED,
        /**
         * The class exists in both generations with different bytes.
         */
        MODIFIED,
        /**
         * The class no longer exists in the new generation.
         */
        REMOVED
    }
}
