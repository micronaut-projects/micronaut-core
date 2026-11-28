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
import io.micronaut.http.client.ExchangeRetryConfiguration;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The retry budget of a service, see {@link ExchangeRetryConfiguration.BudgetConfiguration}: a
 * token bucket shared by its concurrent exchanges. The tokens are counted in thousandths.
 *
 * @since 5.3.0
 */
@Internal
final class RetryBudget {

    private static final long SCALE = 1000;

    private final long maxTokens;
    private final long threshold;
    private final long tokenRatio;
    private final AtomicLong tokens;

    /**
     * @param configuration The configuration of the budget
     */
    RetryBudget(ExchangeRetryConfiguration.BudgetConfiguration configuration) {
        this.maxTokens = configuration.getMaxTokens() * SCALE;
        this.threshold = maxTokens / 2;
        this.tokenRatio = Math.max(1, Math.round(configuration.getTokenRatio() * SCALE));
        this.tokens = new AtomicLong(maxTokens);
    }

    /**
     * An attempt failed with an outcome that is retried: it takes a token.
     *
     * @return Whether the budget allows the retry: more than half of the tokens are left
     */
    boolean onFailure() {
        long left = tokens.updateAndGet(t -> Math.max(0, t - SCALE));
        return left > threshold;
    }

    /**
     * An exchange succeeded: it puts a part of a token back.
     */
    void onSuccess() {
        tokens.updateAndGet(t -> Math.min(maxTokens, t + tokenRatio));
    }
}
