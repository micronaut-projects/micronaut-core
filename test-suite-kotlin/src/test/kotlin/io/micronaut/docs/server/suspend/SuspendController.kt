/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.server.suspend

import io.micronaut.http.*
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Status
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

@Controller("/suspend")
class SuspendController {

    // tag::suspend[]
    @Get("/simple")
    suspend fun simple(): String { // <1>
        return "Hello"
    }
    // end::suspend[]

    // tag::suspendDelayed[]
    @Get("/delayed")
    suspend fun delayed(): String { // <1>
        delay(1) // <2>
        return "Delayed"
    }
    // end::suspendDelayed[]

    // tag::suspendStatus[]
    @Status(HttpStatus.CREATED) // <1>
    @Get("/status")
    suspend fun status(): Unit {
    }
    // end::suspendStatus[]

    // tag::suspendStatusDelayed[]
    @Status(HttpStatus.CREATED)
    @Get("/statusDelayed")
    suspend fun statusDelayed(): Unit {
        delay(1)
    }
    // end::suspendStatusDelayed[]

    val count : AtomicInteger = AtomicInteger(0)

    @Get("/count")
    suspend fun count(): Int { // <1>
        return count.incrementAndGet()
    }

    @Get("/greet")
    suspend fun suspendingGreet(name: String, request: HttpRequest<String>): HttpResponse<out Any> {
        val json = "{\"message\":\"hello\"}"
        return HttpResponse.ok(json).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
    }
}