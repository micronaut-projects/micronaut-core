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
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * A suspend route sees the request it serves before and after suspending, switching dispatchers and calling a
 * declarative suspend client, whose filters also see it, in both propagation modes.
 */
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
abstract class SuspendPropagatedContextTest {

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Test
    fun `the request is propagated across suspensions, dispatcher switches and client calls`() {
        val path = "/suspend-propagated-context"
        // The last value is the caller's path, as seen by the declarative client's filter
        assertEquals(List(5) { path }.joinToString("|"), client.toBlocking().retrieve(HttpRequest.GET<Any>(path)))
    }

    @Requires(property = "spec.name", value = "SuspendPropagatedContextTest")
    @Controller("/suspend-propagated-context")
    class SuspendPropagatedContextController(private val echoClient: EchoClient) {

        @Get
        suspend fun index(): String {
            val before = currentPath()
            delay(10)
            val afterDelay = currentPath()
            val onDefault = withContext(Dispatchers.Default) {
                delay(10)
                currentPath()
            }
            val afterSwitch = currentPath()
            val callerSeenByFilter = echoClient.callerPath()
            return listOf(before, afterDelay, onDefault, afterSwitch, callerSeenByFilter).joinToString("|")
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
