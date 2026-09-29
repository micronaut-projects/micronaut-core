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
package io.micronaut.docs.aop.retry;

import io.micronaut.context.ApplicationContext;
import io.micronaut.retry.CircuitBreakerRegistry;
import io.micronaut.retry.CircuitBreakerSnapshot;
import io.micronaut.retry.CircuitState;
import io.micronaut.retry.exception.CircuitOpenException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NamedCircuitBreakerTest {

    // the configuration of the guide
    private static final Map<String, Object> CONFIGURATION = Map.of(
        "micronaut.retry.circuit-breakers.inventory.reset", "30s",
        "micronaut.retry.circuit-breakers.inventory.attempts", 1,
        "micronaut.retry.circuit-breakers.shipping.reset", "30s",
        "micronaut.retry.circuit-breakers.shipping.request-volume-threshold", 4,
        "micronaut.retry.circuit-breakers.shipping.failure-ratio", 0.5,
        "micronaut.retry.circuit-breakers.shipping.success-threshold", 2,
        "micronaut.retry.circuit-breakers.shipping.skip-on", List.of("java.util.NoSuchElementException")
    );

    @Test
    void testMethodsOfOneNameShareTheCircuit() {
        try (ApplicationContext context = ApplicationContext.run(CONFIGURATION)) {
            InventoryService service = context.getBean(InventoryService.class);
            InventoryClient client = context.getBean(InventoryClient.class);
            Warehouse warehouse = context.getBean(Warehouse.class);
            CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry.class);

            // the attempt and its two retries fail and open the circuit
            assertThrows(IllegalStateException.class, () -> service.stock("apple"));
            assertEquals(3, warehouse.getCalls());
            assertEquals(CircuitState.OPEN, registry.findState("inventory").orElseThrow());

            // the other method and the registry fail fast, without calling the warehouse
            warehouse.setAvailable(true);
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.reserve("apple"));
            assertEquals("Warehouse unavailable", e.getMessage());
            assertThrows(IllegalStateException.class, () -> client.stock("apple"));
            assertEquals(3, warehouse.getCalls());
        }
    }

    @Test
    void testGuardReportsOutcomesToARollingWindow() {
        try (ApplicationContext context = ApplicationContext.run(CONFIGURATION)) {
            InventoryClient client = context.getBean(InventoryClient.class);
            Warehouse warehouse = context.getBean(Warehouse.class);
            CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry.class);

            // two failures: the window of four calls is not full
            assertThrows(IllegalStateException.class, () -> client.ship("order-1"));
            assertThrows(IllegalStateException.class, () -> client.ship("order-1"));
            assertEquals(CircuitState.CLOSED, registry.findState("shipping").orElseThrow());

            // a skipOn exception counts as a success
            warehouse.setAvailable(true);
            assertThrows(NoSuchElementException.class, () -> client.ship("unknown"));
            CircuitBreakerSnapshot snapshot = registry.findSnapshot("shipping").orElseThrow();
            assertEquals(3, snapshot.calls());
            assertEquals(2, snapshot.failures());
            assertEquals(CircuitState.CLOSED, snapshot.state());

            // the fourth call fills the window: two failures of four reach the ratio 0.5
            assertEquals("tracking-order-1", client.ship("order-1"));
            assertEquals(CircuitState.OPEN, registry.findState("shipping").orElseThrow());

            // the guard fails fast
            assertThrows(CircuitOpenException.class, () -> client.ship("order-1"));
            assertEquals(4, warehouse.getCalls());
        }
    }

    @Test
    void testRollingWindowOnTheAnnotation() {
        try (ApplicationContext context = ApplicationContext.run(CONFIGURATION)) {
            PricingService service = context.getBean(PricingService.class);
            Warehouse warehouse = context.getBean(Warehouse.class);
            CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry.class);

            // two calls that fail after their retry: the window of four calls is not full
            assertThrows(IllegalStateException.class, () -> service.price("apple"));
            assertThrows(IllegalStateException.class, () -> service.price("apple"));
            assertEquals(4, warehouse.getCalls());
            CircuitBreakerSnapshot window = registry.findSnapshot("pricing").orElseThrow();
            assertEquals(CircuitState.CLOSED, window.state());
            assertEquals(2, window.calls());
            assertEquals(2, window.failures());

            // a skipOn exception counts as a success
            warehouse.setAvailable(true);
            assertThrows(NoSuchElementException.class, () -> service.price("unknown"));
            assertEquals(CircuitState.CLOSED, registry.findState("pricing").orElseThrow());

            // the fourth call fills the window: two failures of four reach the ratio 0.5
            assertEquals(BigDecimal.TEN, service.price("apple"));
            CircuitBreakerSnapshot snapshot = registry.findSnapshot("pricing").orElseThrow();
            assertEquals(CircuitState.OPEN, snapshot.state());
            assertEquals(4, snapshot.requestVolumeThreshold());
            assertEquals(1, snapshot.openedCount());

            // the open circuit fails fast
            int calls = warehouse.getCalls();
            assertThrows(IllegalStateException.class, () -> service.price("apple"));
            assertEquals(calls, warehouse.getCalls());
        }
    }
}
