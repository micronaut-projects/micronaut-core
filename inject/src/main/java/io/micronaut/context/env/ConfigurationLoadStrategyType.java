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
package io.micronaut.context.env;

import org.jspecify.annotations.NullMarked;

/**
 * Defines how Micronaut should behave when the same configuration resource (for example
 * {@code application.yml} or {@code application.properties}) is found more than once on the classpath.
 *
 * @since 5.0.0
 */
@NullMarked
public enum ConfigurationLoadStrategyType {
    /**
     * The first matching resource is used. Duplicates may be logged as a warning.
     */
    FIRST_MATCH,

    /**
     * All matching resources are read and merged in the configured order.
     */
    MERGE_ALL,

    /**
     * Fail fast if duplicate configuration resources are detected.
     */
    FAIL_ON_DUPLICATE
}
