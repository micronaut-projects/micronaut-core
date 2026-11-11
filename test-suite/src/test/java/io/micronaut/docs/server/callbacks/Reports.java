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
package io.micronaut.docs.server.callbacks;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Requires(property = "spec.name", value = "ReportRoutesTest")
@Singleton
public class Reports {
    final Map<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
    final Map<String, CompletableFuture<Optional<Throwable>>> outcomes = new ConcurrentHashMap<>();

    CompletableFuture<String> generate(String id) {
        return pending.computeIfAbsent(id, k -> new CompletableFuture<>());
    }

    void completed(String id, Throwable failure) {
        outcome(id).complete(Optional.ofNullable(failure));
    }

    CompletableFuture<Optional<Throwable>> outcome(String id) {
        return outcomes.computeIfAbsent(id, k -> new CompletableFuture<>());
    }
}
