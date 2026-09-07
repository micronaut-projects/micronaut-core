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
package io.micronaut.http.server.executeon

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.executor.ThreadSelection
import org.junit.jupiter.api.Test

/**
 * `@ExecuteOn` and `micronaut.server.thread-selection` both resolve through `RouteInfo.getExecutor`, so a suspend
 * route ignored every offloading thread-selection mode as well.
 * See https://github.com/micronaut-projects/micronaut-core/issues/12326.
 */
class SuspendThreadSelectionTest {

    @Test
    fun `thread-selection AUTO offloads both route shapes`() = assertOffloadsBothShapes(ThreadSelection.AUTO)

    @Test
    fun `thread-selection IO offloads both route shapes`() = assertOffloadsBothShapes(ThreadSelection.IO)

    @Test
    fun `thread-selection BLOCKING offloads both route shapes`() = assertOffloadsBothShapes(ThreadSelection.BLOCKING)

    @Test
    fun `thread-selection MANUAL selects no executor, so both shapes stay on the event loop`() {
        val (suspending, blocking) = threadsFor(ThreadSelection.MANUAL)
        assertOnEventLoop(suspending, "thread-selection=MANUAL: a suspend route")
        assertOnEventLoop(blocking, "thread-selection=MANUAL: a blocking route")
    }

    private fun assertOffloadsBothShapes(mode: ThreadSelection) {
        val (suspending, blocking) = threadsFor(mode)
        assertOffloaded(suspending, "thread-selection=$mode: a suspend route")
        assertOffloaded(blocking, "thread-selection=$mode: a blocking route")
    }

    private fun threadsFor(mode: ThreadSelection): Pair<String, String> {
        val configuration = mapOf(
            "spec.name" to "SuspendThreadSelectionTest",
            "micronaut.server.thread-selection" to mode,
        )
        ApplicationContext.run(EmbeddedServer::class.java, configuration).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.uri).use {
                val client = it.toBlocking()
                return client.retrieve("/suspend-thread-selection/suspending") to
                    client.retrieve("/suspend-thread-selection/blocking")
            }
        }
    }

    @Requires(property = "spec.name", value = "SuspendThreadSelectionTest")
    @Controller("/suspend-thread-selection")
    class MyController {
        @Get("/suspending")
        suspend fun suspending(): String = Thread.currentThread().name

        @Get("/blocking")
        fun blocking(): String = Thread.currentThread().name
    }
}
