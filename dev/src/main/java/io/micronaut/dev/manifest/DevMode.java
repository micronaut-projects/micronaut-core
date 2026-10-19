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

import io.micronaut.core.annotation.Experimental;

/**
 * What the development runtime does with a generation.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public enum DevMode {
    /**
     * Runs the application's {@code main}, and starts it again on a new generation after a change.
     */
    RUN,
    /**
     * Runs the tests a change can affect on a new generation after each change; no application runs.
     */
    TEST
}
