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

import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.*
import kotlinx.coroutines.delay

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

    @Get("/illegal")
    suspend fun illegal(): Unit {
        throw IllegalArgumentException()
    }

    @Status(HttpStatus.BAD_REQUEST)
    @Error(exception = IllegalArgumentException::class)
    @Produces(MediaType.TEXT_PLAIN)
    suspend fun onIllegalArgument(e: IllegalArgumentException): String {
        return "illegal.argument"
    }
}