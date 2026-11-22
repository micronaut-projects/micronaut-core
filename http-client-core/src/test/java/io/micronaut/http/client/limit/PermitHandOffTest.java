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
package io.micronaut.http.client.limit;

import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.http.client.exceptions.ConcurrencyLimitExceededException;
import io.micronaut.runtime.ApplicationConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

class PermitHandOffTest {

    private final ClientConcurrencyLimiter limiter = new ClientConcurrencyLimiter("svc", 1);

    @Test
    void anImmediateSelectionKeepsThePermit() {
        Runnable permit = acquire();
        ExecutionFlow<String> flow = PermitHandOff.handOff(ExecutionFlow.just("instance"), permit, ignored -> { });
        Assertions.assertEquals("instance", flow.tryCompleteValue());
        Assertions.assertEquals(1, limiter.getInFlight());
        permit.run();
    }

    @Test
    void anImmediateFailureGivesThePermitBack() {
        Runnable permit = acquire();
        ExecutionFlow<String> flow = PermitHandOff.handOff(ExecutionFlow.error(new IllegalStateException("no instance")), permit, ignored -> { });
        Assertions.assertInstanceOf(IllegalStateException.class, flow.tryCompleteError());
        Assertions.assertEquals(0, limiter.getInFlight());
    }

    @Test
    void aLaterSelectionKeepsThePermit() throws Exception {
        Runnable permit = acquire();
        DelayedExecutionFlow<String> selection = DelayedExecutionFlow.create();
        CompletableFuture<String> result = PermitHandOff.handOff(selection, permit, ignored -> { }).toCompletableFuture();
        selection.complete("instance");
        Assertions.assertEquals("instance", result.get(5, TimeUnit.SECONDS));
        Assertions.assertEquals(1, limiter.getInFlight());
        permit.run();
    }

    @Test
    void aLaterFailureGivesThePermitBack() {
        Runnable permit = acquire();
        DelayedExecutionFlow<String> selection = DelayedExecutionFlow.create();
        CompletableFuture<String> result = PermitHandOff.handOff(selection, permit, ignored -> { }).toCompletableFuture();
        selection.completeExceptionally(new IllegalStateException("no instance"));
        Assertions.assertTrue(result.isCompletedExceptionally());
        Assertions.assertEquals(0, limiter.getInFlight());
    }

    @Test
    void aCancellationBeforeTheSelectionGivesThePermitBackAndReleasesTheLateSelection() {
        Runnable permit = acquire();
        DelayedExecutionFlow<String> selection = DelayedExecutionFlow.create();
        List<String> released = new ArrayList<>();
        ExecutionFlow<String> flow = PermitHandOff.handOff(selection, permit, released::add);
        flow.cancel();
        Assertions.assertEquals(0, limiter.getInFlight());
        Assertions.assertTrue(selection.isCancelled());
        // a future the cancellation could not stop completes anyway: the selection is released
        selection.complete("late");
        Assertions.assertEquals(List.of("late"), released);
        Assertions.assertEquals(0, limiter.getInFlight());
    }

    @Test
    void theExceptionNamesTheLimiter() {
        ConcurrencyLimitExceededException e = new ConcurrencyLimitExceededException("svc", "over the cap");
        Assertions.assertEquals("svc", e.getLimiterName());
        Assertions.assertEquals("over the cap", e.getMessage());
    }

    @Test
    void aNegativeCapIsRejected() {
        ServiceHttpClientConfiguration configuration = new ServiceHttpClientConfiguration("svc", null, null, new ApplicationConfiguration());
        Assertions.assertThrows(IllegalArgumentException.class, () -> configuration.setMaxConcurrentRequests(-1));
        configuration.setMaxConcurrentRequests(0);
        Assertions.assertNull(configuration.getConcurrencyLimiter());
    }

    private Runnable acquire() {
        Runnable permit = limiter.tryAcquire();
        Assertions.assertNotNull(permit);
        return permit;
    }
}
