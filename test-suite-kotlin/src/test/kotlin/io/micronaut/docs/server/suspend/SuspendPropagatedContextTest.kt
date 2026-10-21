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
package io.micronaut.docs.server.suspend

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.netty.util.internal.ThreadExecutorMap
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * A suspend route sees the request it serves before and after suspending, switching dispatchers and calling a
 * declarative suspend client, whose filters also see it, in both propagation modes. Once the client call returns, the
 * route resumes on its own dispatcher rather than on the client's event loop and still sees the request.
 */
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
abstract class SuspendPropagatedContextTest {

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Test
    fun `the request is propagated across suspensions, dispatcher switches and client calls`() {
        assertPropagated("/suspend-propagated-context")
    }

    @Test
    fun `the request is propagated across client calls on an executor dispatcher`() {
        assertPropagated("/suspend-propagated-context/blocking")
    }

    private fun assertPropagated(path: String) {
        // The fifth value is the caller's path, as seen by the declarative client's filter, the sixth the path the
        // route sees once the client call returns and the last whether it then resumed off the client's event loop
        assertEquals(
            List(6) { path }.joinToString("|") + "|$OFF_EVENT_LOOP",
            client.toBlocking().retrieve(HttpRequest.GET<Any>(path))
        )
    }

    @Requires(property = "spec.name", value = "SuspendPropagatedContextTest")
    @Controller("/suspend-propagated-context")
    class SuspendPropagatedContextController(private val echoClient: EchoClient) {

        @Get
        suspend fun index(): String = propagated()

        @Get("/blocking")
        @ExecuteOn(TaskExecutors.BLOCKING)
        suspend fun blocking(): String = propagated()

        private suspend fun propagated(): String {
            val before = currentPath()
            delay(10)
            val afterDelay = currentPath()
            val onDefault = withContext(Dispatchers.Default) {
                delay(10)
                currentPath()
            }
            val afterSwitch = currentPath()
            val callerSeenByFilter = echoClient.callerPath()
            val afterClient = currentPath()
            val thread = if (ThreadExecutorMap.currentExecutor() == null) OFF_EVENT_LOOP else Thread.currentThread().name
            return listOf(before, afterDelay, onDefault, afterSwitch, callerSeenByFilter, afterClient, thread).joinToString("|")
        }

        @Get("/echo")
        fun echo(@Header(CALLER_PATH) callerPath: String): String = callerPath
    }

    @Requires(property = "spec.name", value = "SuspendPropagatedContextTest")
    @Client("/suspend-propagated-context")
    interface EchoClient {

        @Get("/echo")
        suspend fun callerPath(): String
    }

    @Requires(property = "spec.name", value = "SuspendPropagatedContextTest")
    @ClientFilter("/suspend-propagated-context/echo")
    class CallerPathFilter {

        @RequestFilter
        fun addCallerPath(request: MutableHttpRequest<*>) {
            request.header(CALLER_PATH, currentPath() ?: "none")
        }
    }

    companion object {
        private const val CALLER_PATH = "X-Caller-Path"
        private const val OFF_EVENT_LOOP = "off-event-loop"

        private fun currentPath(): String? =
            ServerRequestContext.currentRequest<Any>().map { it.path }.orElse(null)
    }
}

@MicronautTest
@Property(name = "spec.name", value = "SuspendPropagatedContextTest")
@Property(name = "micronaut.propagation", value = "thread-local")
class ThreadLocalSuspendPropagatedContextTest : SuspendPropagatedContextTest()

@MicronautTest
@Property(name = "spec.name", value = "SuspendPropagatedContextTest")
@Property(name = "micronaut.propagation", value = "scoped-value")
class ScopedValueSuspendPropagatedContextTest : SuspendPropagatedContextTest()
