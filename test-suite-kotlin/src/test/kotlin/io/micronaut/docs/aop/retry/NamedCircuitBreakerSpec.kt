/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.docs.aop.retry

import io.micronaut.context.ApplicationContext
import io.micronaut.retry.CircuitBreakerRegistry
import io.micronaut.retry.CircuitState
import io.micronaut.retry.exception.CircuitOpenException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

class NamedCircuitBreakerSpec {

    // the configuration of the guide
    private val configuration: Map<String, Any> = mapOf(
        "micronaut.retry.circuit-breakers.inventory.reset" to "30s",
        "micronaut.retry.circuit-breakers.inventory.attempts" to 1,
        "micronaut.retry.circuit-breakers.shipping.reset" to "30s",
        "micronaut.retry.circuit-breakers.shipping.request-volume-threshold" to 4,
        "micronaut.retry.circuit-breakers.shipping.failure-ratio" to 0.5,
        "micronaut.retry.circuit-breakers.shipping.success-threshold" to 2,
        "micronaut.retry.circuit-breakers.shipping.skip-on" to listOf("java.util.NoSuchElementException")
    )

    @Test
    fun testMethodsOfOneNameShareTheCircuit() {
        ApplicationContext.run(configuration).use { context ->
            val service = context.getBean(InventoryService::class.java)
            val client = context.getBean(InventoryClient::class.java)
            val warehouse = context.getBean(Warehouse::class.java)
            val registry = context.getBean(CircuitBreakerRegistry::class.java)

            // the attempt and its two retries fail and open the circuit
            assertThrows(IllegalStateException::class.java) { service.stock("apple") }
            assertEquals(3, warehouse.calls)
            assertEquals(CircuitState.OPEN, registry.findState("inventory").orElseThrow())

            // the other method and the registry fail fast, without calling the warehouse
            warehouse.available = true
            val e = assertThrows(IllegalStateException::class.java) { service.reserve("apple") }
            assertEquals("Warehouse unavailable", e.message)
            assertThrows(IllegalStateException::class.java) { client.stock("apple") }
            assertEquals(3, warehouse.calls)
        }
    }

    @Test
    fun testGuardReportsOutcomesToARollingWindow() {
        ApplicationContext.run(configuration).use { context ->
            val client = context.getBean(InventoryClient::class.java)
            val warehouse = context.getBean(Warehouse::class.java)
            val registry = context.getBean(CircuitBreakerRegistry::class.java)

            // two failures: the window of four calls is not full
            assertThrows(IllegalStateException::class.java) { client.ship("order-1") }
            assertThrows(IllegalStateException::class.java) { client.ship("order-1") }
            assertEquals(CircuitState.CLOSED, registry.findState("shipping").orElseThrow())

            // a skipOn exception counts as a success
            warehouse.available = true
            assertThrows(NoSuchElementException::class.java) { client.ship("unknown") }
            val snapshot = registry.findSnapshot("shipping").orElseThrow()
            assertEquals(3, snapshot.calls())
            assertEquals(2, snapshot.failures())
            assertEquals(CircuitState.CLOSED, snapshot.state())

            // the fourth call fills the window: two failures of four reach the ratio 0.5
            assertEquals("tracking-order-1", client.ship("order-1"))
            assertEquals(CircuitState.OPEN, registry.findState("shipping").orElseThrow())

            // the guard fails fast
            assertThrows(CircuitOpenException::class.java) { client.ship("order-1") }
            assertEquals(4, warehouse.calls)
        }
    }

    @Test
    fun testRollingWindowOnTheAnnotation() {
        ApplicationContext.run(configuration).use { context ->
            val service = context.getBean(PricingService::class.java)
            val warehouse = context.getBean(Warehouse::class.java)
            val registry = context.getBean(CircuitBreakerRegistry::class.java)

            // two calls that fail after their retry: the window of four calls is not full
            assertThrows(IllegalStateException::class.java) { service.price("apple") }
            assertThrows(IllegalStateException::class.java) { service.price("apple") }
            assertEquals(4, warehouse.calls)
            val window = registry.findSnapshot("pricing").orElseThrow()
            assertEquals(CircuitState.CLOSED, window.state())
            assertEquals(2, window.calls())
            assertEquals(2, window.failures())

            // a skipOn exception counts as a success
            warehouse.available = true
            assertThrows(NoSuchElementException::class.java) { service.price("unknown") }
            assertEquals(CircuitState.CLOSED, registry.findState("pricing").orElseThrow())

            // the fourth call fills the window: two failures of four reach the ratio 0.5
            assertEquals(BigDecimal.TEN, service.price("apple"))
            val snapshot = registry.findSnapshot("pricing").orElseThrow()
            assertEquals(CircuitState.OPEN, snapshot.state())
            assertEquals(4, snapshot.requestVolumeThreshold())
            assertEquals(1L, snapshot.openedCount())

            // the open circuit fails fast
            val calls = warehouse.calls
            assertThrows(IllegalStateException::class.java) { service.price("apple") }
            assertEquals(calls, warehouse.calls)
        }
    }
}
