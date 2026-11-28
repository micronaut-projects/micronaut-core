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

import io.micronaut.retry.annotation.Bulkhead;
import io.micronaut.retry.annotation.TimeLimit;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class ReportService {

    private final Map<String, CompletableFuture<String>> work = new ConcurrentHashMap<>();

    // tag::bulkhead[]
    @Bulkhead(maxConcurrent = "2") // <1>
    public CompletableFuture<String> render(String report) { // <2>
        // ...
    // end::bulkhead[]
        return started(report);
    }

    // tag::named[]
    @Bulkhead(name = "archive", maxConcurrent = "1", maxWait = "5s") // <1>
    public CompletableFuture<String> store(String report) {
        // ...
    // end::named[]
        return started(report);
    }

    // tag::named[]

    @Bulkhead(name = "archive", maxConcurrent = "1", maxWait = "5s") // <2>
    public CompletableFuture<String> load(String report) {
        // ...
    // end::named[]
        return started(report);
    }

    // tag::limit[]
    @TimeLimit("200ms") // <1>
    public CompletableFuture<String> export(String report) { // <2>
        // ...
    // end::limit[]
        return started(report);
    }

    /**
     * @param report A report
     * @return Whether work on it has started
     */
    public boolean isStarted(String report) {
        return work.containsKey(report);
    }

    /**
     * Ends the work on a report.
     *
     * @param report The report
     */
    public void finish(String report) {
        work.get(report).complete(report + " done");
    }

    private CompletableFuture<String> started(String report) {
        CompletableFuture<String> future = new CompletableFuture<>();
        work.put(report, future);
        return future;
    }
}
