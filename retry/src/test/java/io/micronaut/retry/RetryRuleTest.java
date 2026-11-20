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
package io.micronaut.retry;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RetryRule} decides from a {@link RetryOutcome}, {@link RetryPolicy} gives the delays.
 */
class RetryRuleTest {

    @Test
    void ruleOnCodes() {
        RetryRule rule = RetryRule.onCode(code -> code == 503);
        assertTrue(rule.shouldRetry(RetryOutcome.result(503)));
        assertFalse(rule.shouldRetry(RetryOutcome.result(400)));
        assertFalse(rule.shouldRetry(RetryOutcome.failure(new IllegalStateException())));
    }

    @Test
    void ruleOnUnprocessedAndTimeout() {
        RetryRule rule = RetryRule.onUnprocessed().or(RetryRule.onTimeout());
        assertTrue(rule.shouldRetry(RetryOutcome.unprocessed(new IllegalStateException())));
        assertTrue(rule.shouldRetry(RetryOutcome.timeout(new IllegalStateException())));
        assertFalse(rule.shouldRetry(RetryOutcome.failure(new IllegalStateException())));
        assertFalse(rule.shouldRetry(RetryOutcome.result(503)));
        assertFalse(RetryRule.onUnprocessed().and(RetryRule.NEVER).shouldRetry(RetryOutcome.unprocessed(new IllegalStateException())));
    }

    @Test
    void ruleOfAPolicyUsesItsPredicate() {
        RetryPolicy policy = RetryPolicy.builder().excludes(UncheckedIOException.class).build();
        RetryRule rule = RetryRule.of(policy);
        assertTrue(rule.shouldRetry(RetryOutcome.failure(new IllegalStateException())));
        assertFalse(rule.shouldRetry(RetryOutcome.failure(new UncheckedIOException(new IOException()))));
        assertFalse(rule.shouldRetry(RetryOutcome.result(503)), "A policy knows nothing of codes");
    }

    @Test
    void outcomeOfAResultHasNoFailure() {
        assertThrows(IllegalArgumentException.class, () -> new RetryOutcome(RetryOutcome.Kind.RESULT, 200, new IllegalStateException()));
        assertThrows(NullPointerException.class, () -> new RetryOutcome(RetryOutcome.Kind.FAILURE, -1, null));
    }

    @Test
    void nextDelayMultipliesTheDelay() {
        RetryPolicy policy = RetryPolicy.builder().delay(Duration.ofMillis(100)).multiplier(2).build();
        assertEquals(Duration.ofMillis(100), policy.nextDelay(1));
        assertEquals(Duration.ofMillis(200), policy.nextDelay(2));
        assertEquals(Duration.ofMillis(400), policy.nextDelay(3));
        assertThrows(IllegalArgumentException.class, () -> policy.nextDelay(0));
    }

    @Test
    void nextDelayAppliesTheJitter() {
        RetryPolicy policy = RetryPolicy.builder().delay(Duration.ofMillis(1000)).jitter(0.5).build();
        for (int i = 0; i < 20; i++) {
            long delay = policy.nextDelay(1).toMillis();
            assertTrue(delay >= 500 && delay <= 1500, "delay " + delay);
        }
    }
}
