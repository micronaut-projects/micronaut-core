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

import io.micronaut.retry.CircuitBreakerGuard
import io.micronaut.retry.CircuitBreakerOperations
import io.micronaut.retry.CircuitBreakerRegistry
import jakarta.inject.Singleton

@Singleton
// tag::registry[]
open class InventoryClient(
    registry: CircuitBreakerRegistry,
    private val warehouse: Warehouse
) {
    private val inventory: CircuitBreakerOperations = registry.circuitBreaker("inventory") // <1>
    private val shipping: CircuitBreakerGuard = registry.guard("shipping") // <2>

    open fun stock(sku: String): Int {
        return inventory.execute { warehouse.stock(sku) } // <3>
    }
    // end::registry[]

    // tag::guard[]
    open fun ship(order: String): String {
        val permit = shipping.acquire() // <1>
        try {
            val tracking = warehouse.ship(order)
            permit.onSuccess() // <2>
            return tracking
        } catch (e: RuntimeException) {
            permit.onFailure(e) // <3>
            throw e
        }
    }
    // end::guard[]
}
