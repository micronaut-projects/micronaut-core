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

import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A remote warehouse of the circuit breaker examples: it fails while it is unavailable.
 */
@Singleton
public class Warehouse {

    private final AtomicInteger calls = new AtomicInteger();
    private volatile boolean available;

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public int getCalls() {
        return calls.get();
    }

    public int stock(String sku) {
        call();
        find(sku);
        return 10;
    }

    public void reserve(String sku) {
        call();
        find(sku);
    }

    public String ship(String order) {
        call();
        return "tracking-" + find(order);
    }

    public BigDecimal price(String sku) {
        call();
        find(sku);
        return BigDecimal.TEN;
    }

    private void call() {
        calls.incrementAndGet();
        if (!available) {
            throw new IllegalStateException("Warehouse unavailable");
        }
    }

    private static String find(String id) {
        if ("unknown".equals(id)) {
            throw new NoSuchElementException("Unknown: " + id);
        }
        return id;
    }
}
