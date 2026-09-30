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

import io.micronaut.retry.annotation.CircuitBreaker
import jakarta.inject.Singleton

@Singleton
class InventoryService {

    private final Warehouse warehouse

    InventoryService(Warehouse warehouse) {
        this.warehouse = warehouse
    }

    // tag::named[]
    @CircuitBreaker(name = "inventory", attempts = "2") // <1>
    int stock(String sku) {
        warehouse.stock(sku)
    }

    @CircuitBreaker(name = "inventory") // <2>
    void reserve(String sku) {
        warehouse.reserve(sku)
    }
    // end::named[]
}
