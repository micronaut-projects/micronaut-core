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

/**
 * How a test ended.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public enum TestStatus {
    /**
     * It passed.
     */
    PASSED,
    /**
     * An assertion failed.
     */
    FAILED,
    /**
     * It threw something other than an assertion failure, or could not run, as when its class failed to set up.
     */
    ERRORED,
    /**
     * It was disabled, or an assumption did not hold.
     */
    SKIPPED;

    /**
     * @return Whether the status fails the run
     */
    public boolean isFailure() {
        return this == FAILED || this == ERRORED;
    }
}
