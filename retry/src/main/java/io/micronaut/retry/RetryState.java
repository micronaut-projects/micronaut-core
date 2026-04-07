/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.retry;

import org.jspecify.annotations.Nullable;
import io.micronaut.retry.annotation.RetryPredicate;

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
     * Should a retry attempt to occur.
     *
     * @param exception The error
     * @return True if it should
     */
    boolean canRetry(Throwable exception);

    /**
     * Returns the maximum number of attempts.
     *
     * @return The maximum number of attempts
     */
    int getMaxAttempts();

    /**
     * Returns the number of the current attempt.
     *
     * @return The number of the current attempt
     */
    int currentAttempt();

    /**
     * Returns the multiplier to use between delays.
     *
     * @return The multiplier to use between delays
     */
    OptionalDouble getMultiplier();

    /**
     * Returns the delay between attempts.
     *
     * @return The delay between attempts
     */
    Duration getDelay();

    /**
     * Returns the overall delay so far.
     *
     * @return The overall delay so far
     */
    Duration getOverallDelay();

    /**
     * Returns the maximum overall delay.
     *
     * @return The maximum overall delay
     */
    Optional<Duration> getMaxDelay();

    /**
     * Returns the jitter factor used to apply random deviation to retry delays.
     *
     * @return The jitter factor used to apply random deviation to retry delays
     */
    default OptionalDouble getJitter() {
        return OptionalDouble.empty();
    }

    /**
     * Returns the retry predicate checking for includes and excludes throwable classes.
     *
     * @return The retry predicate checking for includes and excludes throwable classes
     */
    default RetryPredicate getRetryPredicate() {
        throw new UnsupportedOperationException("Retry predicate not supported on this type");
    }

    /**
     * Returns the captured exception type, which defaults to {@link RuntimeException}.
     *
     * @return The captured exception type
     */
    @Nullable
    Class<? extends Throwable> getCapturedException();

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
