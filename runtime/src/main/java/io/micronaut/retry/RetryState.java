/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.retry;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * An interface that encapsulates the current state of a {@link io.micronaut.retry.annotation.Retryable} operation.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface RetryState {

    /**
     * Should a retry attempt occur.
     *
     * @param exception The error
     *
     * @return True if it should
     */
    boolean canRetry(Throwable exception);

    /**
     * @return The maximum number of attempts
     */
    int getMaxAttempts();

    /**
     * @return The number of the current attempt
     */
    int currentAttempt();

    /**
     * @return The multiplier to use between delays
     */
    OptionalDouble getMultiplier();

    /**
     * @return The delay between attempts
     */
    Duration getDelay();

    /**
     * @return The overall delay so far
     */
    Duration getOverallDelay();

    /**
     * @return The maximum overall delay
     */
    Optional<Duration> getMaxDelay();

    /**
     * Opens the retry state.
     */
    default void open() {
        // no-op for stateless retry
    }

    /**
     * Closes the {@link RetryState}.
     *
     * @param exception An exception if an error occurred or null if the operation completed as expected
     */
    default void close(@Nullable Throwable exception) {
        // no-op for stateless retry
    }
}
