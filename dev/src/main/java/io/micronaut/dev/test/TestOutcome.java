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
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;

/**
 * How a test ended and how long it took.
 *
 * @param status How it ended
 * @param duration How long it ran
 * @param failure Why it failed or errored, if it did
 * @param skipReason Why it was skipped, if it was and a reason is known
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public record TestOutcome(TestStatus status, Duration duration, @Nullable TestFailure failure, @Nullable String skipReason) {

    /**
     * Validating constructor.
     *
     * @param status The status
     * @param duration The duration
     * @param failure The failure
     * @param skipReason The skip reason
     */
    public TestOutcome {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(duration, "duration");
    }
}
