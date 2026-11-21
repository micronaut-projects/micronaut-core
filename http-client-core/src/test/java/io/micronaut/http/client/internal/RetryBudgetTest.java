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

import io.micronaut.http.client.ExchangeRetryConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryBudgetTest {

    @Test
    void retriesWhileMoreThanHalfOfTheTokensAreLeft() {
        ExchangeRetryConfiguration.BudgetConfiguration configuration = new ExchangeRetryConfiguration.BudgetConfiguration();
        configuration.setMaxTokens(4);
        configuration.setTokenRatio(0.5);
        RetryBudget budget = new RetryBudget(configuration);
        assertTrue(budget.onFailure(), "3 tokens left");
        assertFalse(budget.onFailure(), "2 tokens left, the half");
        assertFalse(budget.onFailure(), "1 token left");
        assertFalse(budget.onFailure(), "none left");
        assertFalse(budget.onFailure(), "never below none");
        // each success puts half a token back
        for (int i = 0; i < 7; i++) {
            budget.onSuccess();
        }
        assertTrue(budget.onFailure(), "3.5 tokens back, 2.5 after the failure");
        assertFalse(budget.onFailure());
    }

    @Test
    void neverHoldsMoreThanTheMaximum() {
        ExchangeRetryConfiguration.BudgetConfiguration configuration = new ExchangeRetryConfiguration.BudgetConfiguration();
        configuration.setMaxTokens(2);
        RetryBudget budget = new RetryBudget(configuration);
        for (int i = 0; i < 100; i++) {
            budget.onSuccess();
        }
        assertFalse(budget.onFailure(), "a full bucket of 2 tokens has 1 left after a failure, the half");
    }
}
