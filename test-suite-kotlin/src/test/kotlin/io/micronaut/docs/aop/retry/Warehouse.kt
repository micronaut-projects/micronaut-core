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

import jakarta.inject.Singleton
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger

/**
 * A remote warehouse of the circuit breaker examples: it fails while it is unavailable.
 */
@Singleton
open class Warehouse {

    private val callCount = AtomicInteger()

    @Volatile
    var available = false

    val calls: Int
        get() = callCount.get()

    open fun stock(sku: String): Int {
        call()
        return 10
    }

    open fun reserve(sku: String) {
        call()
    }

    open fun ship(order: String): String {
        call()
        return "tracking-" + find(order)
    }

    open fun price(sku: String): BigDecimal {
        call()
        find(sku)
        return BigDecimal.TEN
    }

    private fun call() {
        callCount.incrementAndGet()
        if (!available) {
            throw IllegalStateException("Warehouse unavailable")
        }
    }

    private fun find(id: String): String {
        if (id == "unknown") {
            throw NoSuchElementException("Unknown: $id")
        }
        return id
    }
}
