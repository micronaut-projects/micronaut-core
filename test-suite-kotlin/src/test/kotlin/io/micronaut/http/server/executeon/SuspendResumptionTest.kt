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

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * The route's executor must also be the dispatcher the coroutine resumes on, otherwise work after a suspension
 * point runs on `Dispatchers.Default` regardless of [ExecuteOn].
 * See https://github.com/micronaut-projects/micronaut-core/issues/12326.
 */
@MicronautTest
@Property(name = "spec.name", value = "SuspendResumptionTest")
class SuspendResumptionTest {
    @Inject
    lateinit var server: EmbeddedServer

    @Test
    fun `a suspend route with ExecuteOn stays on that executor across a suspension point`() {
        val (before, after) = threadsFor("/executeOn")
        assertOnIoExecutor(before, "a suspend route with @ExecuteOn(IO)")
        assertOnIoExecutor(after, "a suspend route with @ExecuteOn(IO), after resuming")
    }

    @Test
    fun `a route without ExecuteOn is not diverted onto the executor`() {
        val (before, after) = threadsFor("/plain")
        assertOnEventLoop(before, "a suspend route with no executor")
        assertNotOnIoExecutor(after, "a suspend route with no executor, after resuming")
    }

    @Test
    fun `child coroutines of a route with ExecuteOn inherit that executor`() {
        val (first, second) = threadsFor("/nested-executeOn")
        assertOnIoExecutor(first, "the first child coroutine of a route with @ExecuteOn(IO)")
        assertOnIoExecutor(second, "the second child coroutine of a route with @ExecuteOn(IO)")
    }

    @Test
    fun `child coroutines of a route without ExecuteOn are not diverted onto the executor`() {
        val (first, second) = threadsFor("/nested-plain")
        assertNotOnIoExecutor(first, "the first child coroutine of a route with no executor")
        assertNotOnIoExecutor(second, "the second child coroutine of a route with no executor")
    }

    @Test
    fun `an explicit dispatcher wins over the route's executor, which is restored afterwards`() {
        val (inside, after) = threadsFor("/explicit-dispatcher")
        assertNotOnIoExecutor(inside, "a withContext(Dispatchers.Default) block")
        assertOnIoExecutor(after, "a route with @ExecuteOn(IO), after a withContext block returns")
    }

    /**
     * The dispatcher is resolved once per executor rather than per request. A coroutine dispatcher is compared by
     * identity, so a fresh one each time would allocate on the request path and stop `withContext` from recognising
     * the route's own dispatcher as the one already in effect.
     */
    @Test
    fun `the route's dispatcher is the same instance across requests`() {
        val (first, _) = threadsFor("/dispatcher-identity")
        val (second, _) = threadsFor("/dispatcher-identity")
        Assertions.assertEquals(first, second, "the route's dispatcher was rebuilt for the second request")
    }

    /**
     * The dispatcher is built from the raw executor rather than the context propagating wrapper, so the propagated
     * context has to survive a suspension point on the strength of KotlinCoroutinePropagation alone.
     */
    @Test
    fun `the request is still propagated after resuming on the executor`() {
        val (before, after) = threadsFor("/propagation")
        Assertions.assertEquals("/suspend-resumption/propagation", before, "the request was missing before suspending")
        Assertions.assertEquals("/suspend-resumption/propagation", after, "the request was missing after resuming")
    }

    private fun threadsFor(path: String): Pair<String, String> =
        server.applicationContext.createBean(HttpClient::class.java, server.uri).use {
            val (before, after) = it.toBlocking().retrieve("/suspend-resumption$path").split("|")
            before to after
        }

    @Requires(property = "spec.name", value = "SuspendResumptionTest")
    @Controller("/suspend-resumption")
    class MyController {
        @Get("/executeOn")
        @ExecuteOn(TaskExecutors.IO)
        suspend fun withExecuteOn(): String = beforeAndAfterSuspension()

        @Get("/plain")
        suspend fun plain(): String = beforeAndAfterSuspension()

        @Get("/nested-executeOn")
        @ExecuteOn(TaskExecutors.IO)
        suspend fun nestedWithExecuteOn(): String = threadsOfChildCoroutines()

        @Get("/nested-plain")
        suspend fun nestedPlain(): String = threadsOfChildCoroutines()

        @Get("/dispatcher-identity")
        @ExecuteOn(TaskExecutors.IO)
        suspend fun dispatcherIdentity(): String {
            val before = dispatcherId()
            delay(5.milliseconds)
            return "$before|${dispatcherId()}"
        }

        @Get("/propagation")
        @ExecuteOn(TaskExecutors.IO)
        suspend fun propagation(): String {
            val before = currentPath()
            delay(5.milliseconds)
            return "$before|${currentPath()}"
        }

        @Get("/explicit-dispatcher")
        @ExecuteOn(TaskExecutors.IO)
        suspend fun explicitDispatcher(): String {
            val inside = withContext(Dispatchers.Default) { Thread.currentThread().name }
            return "$inside|${Thread.currentThread().name}"
        }

        private suspend fun dispatcherId(): String =
            System.identityHashCode(coroutineContext[ContinuationInterceptor]).toString()

        private fun currentPath(): String =
            ServerRequestContext.currentRequest<Any>().map { it.path }.orElse("<no request>")

        private suspend fun beforeAndAfterSuspension(): String {
            val before = Thread.currentThread().name
            delay(5.milliseconds)
            return "$before|${Thread.currentThread().name}"
        }

        // children inherit the dispatcher from the route's coroutine context, so @ExecuteOn reaches them too
        private suspend fun threadsOfChildCoroutines(): String = coroutineScope {
            val first = async { Thread.currentThread().name }
            val second = async { Thread.currentThread().name }
            "${first.await()}|${second.await()}"
        }
    }
}
