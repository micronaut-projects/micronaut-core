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
package io.micronaut.docs.aop.retry

import io.micronaut.retry.annotation.Bulkhead
import io.micronaut.retry.annotation.TimeLimit
import jakarta.inject.Singleton
import kotlinx.coroutines.future.await
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Singleton
open class ReportService {

    private val work = ConcurrentHashMap<String, CompletableFuture<String>>()

    // tag::bulkhead[]
    @Bulkhead(maxConcurrent = "2") // <1>
    open fun render(report: String): CompletableFuture<String> { // <2>
        // ...
    // end::bulkhead[]
        return started(report)
    }

    // tag::named[]
    @Bulkhead(name = "archive", maxConcurrent = "1", maxWait = "5s") // <1>
    open fun store(report: String): CompletableFuture<String> {
        // ...
    // end::named[]
        return started(report)
    }

    // tag::named[]

    @Bulkhead(name = "archive", maxConcurrent = "1", maxWait = "5s") // <2>
    open fun load(report: String): CompletableFuture<String> {
        // ...
    // end::named[]
        return started(report)
    }

    // tag::limit[]
    @TimeLimit("200ms") // <1>
    open suspend fun export(report: String): String { // <2>
        // ...
    // end::limit[]
        return started(report).await()
    }

    /**
     * Whether work on a report has started.
     */
    fun isStarted(report: String): Boolean = work.containsKey(report)

    /**
     * Ends the work on a report.
     */
    fun finish(report: String) {
        work[report]!!.complete("$report done")
    }

    private fun started(report: String): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        work[report] = future
        return future
    }
}
