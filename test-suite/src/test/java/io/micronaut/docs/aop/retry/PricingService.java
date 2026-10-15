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

import io.micronaut.retry.annotation.CircuitBreaker;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.util.NoSuchElementException;

@Singleton
public class PricingService {

    private final Warehouse warehouse;

    public PricingService(Warehouse warehouse) {
        this.warehouse = warehouse;
    }

    // tag::window[]
    @CircuitBreaker(name = "pricing",
        attempts = "1", // <1>
        reset = "30s",
        requestVolumeThreshold = "4", // <2>
        failureRatio = "0.5", // <3>
        successThreshold = "2", // <4>
        skipOn = NoSuchElementException.class) // <5>
    public BigDecimal price(String sku) {
        return warehouse.price(sku);
    }
    // end::window[]
}
