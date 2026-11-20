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
package io.micronaut.http.client.internal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.retry.RetryPolicy;

import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * Decides from the {@link RetryOutcome outcome} of an attempt whether the operation is retried,
 * e.g. on a status code, on a failure, or on an attempt that was not processed at all. How long
 * to wait before the retry, and how many attempts there are, stays with the
 * {@link RetryPolicy}.
 *
 * @since 5.3.0
 */
@Internal
@FunctionalInterface
public interface RetryRule {

    /**
     * A rule that never retries.
     */
    RetryRule NEVER = outcome -> false;

    /**
     * @param outcome The outcome of the attempt
     * @return Whether the operation is retried, if the policy has attempts left
     */
    boolean shouldRetry(RetryOutcome outcome);

    /**
     * @param other Another rule
     * @return A rule that retries when this rule or the other does
     */
    default RetryRule or(RetryRule other) {
        Objects.requireNonNull(other, "other");
        return outcome -> shouldRetry(outcome) || other.shouldRetry(outcome);
    }

    /**
     * @param other Another rule
     * @return A rule that retries when both this rule and the other do
     */
    default RetryRule and(RetryRule other) {
        Objects.requireNonNull(other, "other");
        return outcome -> shouldRetry(outcome) && other.shouldRetry(outcome);
    }

    /**
     * @param codes The codes to retry, e.g. HTTP status codes
     * @return A rule that retries a result whose code matches
     */
    static RetryRule onCode(IntPredicate codes) {
        Objects.requireNonNull(codes, "codes");
        return outcome -> outcome.kind() == RetryOutcome.Kind.RESULT && codes.test(outcome.code());
    }

    /**
     * @param failures The failures to retry, e.g. the predicate of a {@link RetryPolicy}
     * @return A rule that retries a failure, a timeout or an unprocessed attempt whose failure
     * matches
     */
    static RetryRule onFailure(Predicate<? super Throwable> failures) {
        Objects.requireNonNull(failures, "failures");
        return outcome -> {
            Throwable failure = outcome.failure();
            if (failure == null) {
                // a result
                return false;
            }
            return failures.test(failure);
        };
    }

    /**
     * @return A rule that retries an attempt that was not processed at all
     */
    static RetryRule onUnprocessed() {
        return outcome -> outcome.kind() == RetryOutcome.Kind.UNPROCESSED;
    }

    /**
     * @return A rule that retries an attempt that did not complete in time
     */
    static RetryRule onTimeout() {
        return outcome -> outcome.kind() == RetryOutcome.Kind.TIMEOUT;
    }

    /**
     * @param policy The policy
     * @return A rule that retries the failures the predicate of the policy retries: a result
     * is not retried by a policy, which knows nothing of result codes
     */
    static RetryRule of(RetryPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        return outcome -> {
            Throwable failure = outcome.failure();
            if (failure == null) {
                // a result
                return false;
            }
            return policy.capturedException().isInstance(failure) && policy.predicate().test(failure);
        };
    }
}
