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
package io.micronaut.http.server.kotlinx.noreactor

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Status
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.server.CoroutineHelper
import io.micronaut.http.bind.binders.ContinuationArgumentBinder
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher

/**
 * Kotlin suspend routes when kotlinx-coroutines-reactor is not on the classpath, so the route is not wrapped in a
 * Reactor context.
 */
@MicronautTest
@Property(name = "spec.name", value = "SuspendRouteWithoutReactorTest")
class SuspendRouteWithoutReactorTest {

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Inject
    lateinit var coroutineHelper: CoroutineHelper

    private fun get(path: String): HttpResponse<String> =
        client.toBlocking().exchange(HttpRequest.GET<Any>("/no-reactor$path"), String::class.java)

    @Test
    fun `the Reactor coroutine integration is absent`() {
        assertThrows(ClassNotFoundException::class.java) { Class.forName("kotlinx.coroutines.reactor.ReactorContext") }
        assertThrows(ClassNotFoundException::class.java) { Class.forName("kotlinx.coroutines.reactive.ReactiveFlowKt") }
        assertFalse(ContinuationArgumentBinder.isReactorContextPropagated())
        assertFalse(coroutineHelper.isReactorContextPropagated)
    }

    @Test
    fun `a value produced after suspending is the body`() {
        val response = get("/value")
        assertEquals(HttpStatus.OK, response.status)
        assertEquals("value /no-reactor/value", response.body())
    }

    @Test
    fun `a unit route uses the declared status`() {
        val response = client.toBlocking().exchange(HttpRequest.POST("/no-reactor/unit", ""), String::class.java)
        assertEquals(HttpStatus.CREATED, response.status)
        assertNull(response.body())
    }

    @Test
    fun `a null result is not found`() {
        val e = assertThrows(HttpClientResponseException::class.java) { get("/null") }
        assertEquals(HttpStatus.NOT_FOUND, e.status)
    }

    @Test
    fun `an exception is an internal server error`() {
        val e = assertThrows(HttpClientResponseException::class.java) { get("/error") }
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.status)
    }

    @Test
    fun `an http response is returned as is`() {
        val response = get("/response")
        assertEquals(HttpStatus.ACCEPTED, response.status)
        assertEquals("accepted", response.body())
        assertEquals("yes", response.header("X-Suspend"))
    }

    @Test
    fun `a route with execute on runs on the blocking executor`() {
        val response = get("/blocking")
        assertEquals(HttpStatus.OK, response.status)
        assertTrue(response.body()!!.startsWith("virtual") || response.body()!!.startsWith("io"), response.body())
    }

    @Test
    fun `the propagated context of a filter is visible in the coroutine`() {
        val response = client.toBlocking().exchange(
            HttpRequest.GET<Any>("/no-reactor/propagated").header("X-Value", "abc"), String::class.java)
        assertEquals("abc abc", response.body())
    }

    data class ValueElement(val value: String) : PropagatedContextElement

    @Requires(property = "spec.name", value = "SuspendRouteWithoutReactorTest")
    @Filter("/no-reactor/propagated")
    class ValueFilter : HttpServerFilter {
        override fun doFilter(request: HttpRequest<*>, chain: ServerFilterChain): Publisher<MutableHttpResponse<*>> {
            val value = request.headers["X-Value"] ?: "none"
            PropagatedContext.getOrEmpty().plus(ValueElement(value)).propagate().use {
                return chain.proceed(request)
            }
        }
    }

    @Requires(property = "spec.name", value = "SuspendRouteWithoutReactorTest")
    @Controller("/no-reactor")
    class SuspendController {

        @Get("/value")
        suspend fun value(): String {
            delay(10)
            return "value " + ServerRequestContext.currentRequest<Any>().orElseThrow().path
        }

        @Post("/unit")
        @Status(HttpStatus.CREATED)
        suspend fun unit() {
            delay(10)
        }

        @Get("/null")
        suspend fun nothing(): String? {
            delay(10)
            return null
        }

        @Get("/error")
        suspend fun error(): String {
            delay(10)
            throw IllegalStateException("failed after suspending")
        }

        @Get("/response")
        suspend fun response(): HttpResponse<String> {
            delay(10)
            return HttpResponse.accepted<String>().body("accepted").header("X-Suspend", "yes")
        }

        @Get("/blocking")
        @ExecuteOn(TaskExecutors.BLOCKING)
        suspend fun blocking(): String {
            val before = Thread.currentThread()
            delay(10)
            val after = Thread.currentThread()
            check(!after.name.contains("eventloop")) { "resumed on " + after.name }
            return (if (before.isVirtual) "virtual " else "io ") + before.name
        }

        @Get("/propagated")
        suspend fun propagated(): String {
            val before = PropagatedContext.get().find(ValueElement::class.java).map { it.value }.orElse("missing")
            delay(10)
            val after = PropagatedContext.get().find(ValueElement::class.java).map { it.value }.orElse("missing")
            return "$before $after"
        }
    }
}
