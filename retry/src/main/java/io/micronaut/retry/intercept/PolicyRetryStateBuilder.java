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
package io.micronaut.retry.intercept;

import io.micronaut.core.annotation.Internal;
import io.micronaut.retry.RetryPolicy;
import io.micronaut.retry.RetryState;
import io.micronaut.retry.RetryStateBuilder;

/**
 * Builds retry state from a typed retry policy.
 *
 * @author graemerocher
 * @since 5.0.0
 */
@Internal
public final class PolicyRetryStateBuilder implements RetryStateBuilder {

    private final RetryPolicy retryPolicy;

    /**
     * Creates a retry state builder from a typed retry policy.
     *
     * @param retryPolicy The retry policy
     */
    public PolicyRetryStateBuilder(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    /**
     * Builds retry state from the configured retry policy.
     *
     * @return The retry state
     */
    @Override
    public RetryState build() {
        return new SimpleRetry(
            retryPolicy.maxAttempts(),
            retryPolicy.multiplier(),
            retryPolicy.delay(),
            retryPolicy.getMaxDelay().orElse(null),
            retryPolicy.predicate(),
            retryPolicy.capturedException(),
            retryPolicy.jitter()
        );
    }
}
