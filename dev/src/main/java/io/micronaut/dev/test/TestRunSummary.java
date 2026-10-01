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

import java.time.Duration;
import java.util.Objects;

/**
 * What a run did.
 *
 * @param runId The run's identifier
 * @param passed How many tests passed
 * @param failed How many failed an assertion
 * @param errored How many errored, or could not run
 * @param skipped How many were skipped
 * @param duration How long the run took
 * @param cancelled Whether the run was cancelled before it finished
 * @param complete Whether every selected test was attempted: false when the run was cancelled or the runner
 *                 failed before it could run them all, so its results say nothing of the tests it did not reach
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public record TestRunSummary(String runId, int passed, int failed, int errored, int skipped, Duration duration, boolean cancelled, boolean complete) {

    /**
     * Validating constructor.
     *
     * @param runId The identifier
     * @param passed The passed count
     * @param failed The failed count
     * @param errored The errored count
     * @param skipped The skipped count
     * @param duration The duration
     * @param cancelled Whether it was cancelled
     * @param complete Whether every selected test was attempted
     */
    public TestRunSummary {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(duration, "duration");
        if (cancelled && complete) {
            throw new IllegalArgumentException("A cancelled run is not complete");
        }
    }

    /**
     * @return How many tests ran or were skipped
     */
    public int total() {
        return passed + failed + errored + skipped;
    }

    /**
     * @return Whether no test failed or errored and the run attempted every selected test
     */
    public boolean isSuccess() {
        return failed == 0 && errored == 0 && complete;
    }
}
