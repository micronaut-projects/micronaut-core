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
package io.micronaut.dev.test;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/**
 * One test of a run, as reports name it.
 *
 * @param uniqueId The runner's identifier of the test, unique within a run
 * @param className What a report groups the test under: the test class, or for a test that has no class,
 *                  such as a pytest function, the file that declares it
 * @param name The test's name within its class, as reports show it
 * @param displayName The name to show a person, which may differ from {@code name}
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public record TestId(String uniqueId, String className, String name, String displayName) {

    /**
     * Validating constructor.
     *
     * @param uniqueId The identifier
     * @param className The class name
     * @param name The name
     * @param displayName The display name
     */
    public TestId {
        Objects.requireNonNull(uniqueId, "uniqueId");
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(displayName, "displayName");
    }
}
