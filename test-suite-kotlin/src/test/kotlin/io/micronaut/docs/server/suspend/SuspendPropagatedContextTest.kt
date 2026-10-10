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
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
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
 * A suspend route sees the request it serves before and after suspending and switching dispatchers, in both
 * propagation modes.
 */
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
abstract class SuspendPropagatedContextTest {

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Test
    fun `the request is propagated across suspensions and dispatcher switches`() {
        val path = "/suspend-propagated-context"
        assertEquals(List(4) { path }.joinToString("|"), client.toBlocking().retrieve(HttpRequest.GET<Any>(path)))
    }

    @Requires(property = "spec.name", value = "SuspendPropagatedContextTest")
    @Controller("/suspend-propagated-context")
    class SuspendPropagatedContextController {

        @Get
        suspend fun index(): String {
            val before = currentPath()
            delay(10)
            val afterDelay = currentPath()
            val onDefault = withContext(Dispatchers.Default) {
                delay(10)
                currentPath()
            }
            return listOf(before, afterDelay, onDefault, currentPath()).joinToString("|")
        }

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
