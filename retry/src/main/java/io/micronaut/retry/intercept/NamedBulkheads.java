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
package io.micronaut.retry.intercept;

import io.micronaut.core.annotation.Internal;
import io.micronaut.retry.BulkheadRegistry;
import io.micronaut.retry.BulkheadSnapshot;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The bulkheads that have a name, shared by the methods annotated {@code @Bulkhead(name = "...")}.
 * Every method of a name declares the same number of concurrent calls: one that disagrees with the
 * method that created the bulkhead fails, with both named.
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Internal
@Singleton
public final class NamedBulkheads implements BulkheadRegistry {
    private final Map<String, BulkheadPermits> bulkheads = new ConcurrentHashMap<>();

    BulkheadPermits join(String name, int maxConcurrent, String user) {
        BulkheadPermits permits = bulkheads.computeIfAbsent(name, n -> new BulkheadPermits(n, maxConcurrent, user));
        if (permits.maxConcurrent() != maxConcurrent) {
            throw new IllegalStateException("The bulkhead [" + name + "] runs " + permits.maxConcurrent()
                + " calls at the same time, as " + permits.user() + " declared it, and " + user + " declares "
                + maxConcurrent + ": the methods of one bulkhead declare the same number");
        }
        return permits;
    }

    @Override
    public Set<String> getNames() {
        return Collections.unmodifiableSet(new TreeSet<>(bulkheads.keySet()));
    }

    @Override
    public Optional<BulkheadSnapshot> findSnapshot(String name) {
        BulkheadPermits permits = bulkheads.get(name);
        return permits == null ? Optional.empty() : Optional.of(permits.snapshot());
    }
}
