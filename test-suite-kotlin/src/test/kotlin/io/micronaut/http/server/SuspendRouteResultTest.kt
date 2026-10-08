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
package io.micronaut.http.server

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Status
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.delay
import reactor.core.publisher.Flux
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * The results a suspend route completes with after it has actually suspended: a value, nothing (Unit or null),
 * an HTTP response with a streamed body, or an exception.
 */
@MicronautTest
@Property(name = "spec.name", value = "SuspendRouteResultTest")
class SuspendRouteResultTest {

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    private fun get(path: String): HttpResponse<String> =
        client.toBlocking().exchange(HttpRequest.GET<Any>("/suspend-result$path"), String::class.java)

    @Test
    fun `a value produced after suspending is the body`() {
        val response = get("/value")
        assertEquals(HttpStatus.OK, response.status)
        assertEquals("value /suspend-result/value", response.body())
    }

    @Test
    fun `a Unit result after suspending has no body and keeps the declared status`() {
        val response = get("/unit")
        assertEquals(HttpStatus.CREATED, response.status)
        assertEquals(null, response.body())
    }

    @Test
    fun `a null result after suspending is a not found`() {
        val ex = assertThrows(HttpClientResponseException::class.java) { get("/null") }
        assertEquals(HttpStatus.NOT_FOUND, ex.status)
    }

    @Test
    fun `an exception thrown after suspending reaches the error handling`() {
        val ex = assertThrows(HttpClientResponseException::class.java) { get("/error") }
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.status)
    }

    @Test
    fun `an HTTP response with a streamed body after suspending is streamed`() {
        val response = get("/flow")
        assertEquals(HttpStatus.ACCEPTED, response.status)
        assertEquals("[a,b]", response.body())
    }

    @Requires(property = "spec.name", value = "SuspendRouteResultTest")
    @Controller("/suspend-result")
    class SuspendResultController {

        @Get("/value", produces = [MediaType.TEXT_PLAIN])
        suspend fun value(): String {
            delay(1)
            return "value " + ServerRequestContext.currentRequest<Any>().orElseThrow().path
        }

        @Status(HttpStatus.CREATED)
        @Get("/unit")
        suspend fun unit() {
            delay(1)
        }

        @Get("/null")
        suspend fun nothing(): String? {
            delay(1)
            return null
        }

        @Get("/error")
        suspend fun error(): String {
            delay(1)
            throw IllegalArgumentException("bad")
        }

        @Get("/flow", produces = [MediaType.APPLICATION_JSON])
        suspend fun flux(): HttpResponse<Flux<String>> {
            delay(1)
            return HttpResponse.accepted<Flux<String>>().body(Flux.just("a", "b"))
        }
    }
}
