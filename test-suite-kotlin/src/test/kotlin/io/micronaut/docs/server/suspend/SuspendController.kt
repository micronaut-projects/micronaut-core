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
import io.micronaut.http.annotation.*
import io.micronaut.scheduling.TaskExecutors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Named

@Controller("/suspend")
class SuspendController(@Named(TaskExecutors.IO) private val executor: ExecutorService, private val suspendService: SuspendService) {

    private val coroutineDispatcher: CoroutineDispatcher

    init {
        coroutineDispatcher = executor.asCoroutineDispatcher()
    }

    // tag::suspend[]
    @Get("/simple", produces = [MediaType.TEXT_PLAIN])
    suspend fun simple(): String { // <1>
        return "Hello"
    }
    // end::suspend[]

    // tag::suspendDelayed[]
    @Get("/delayed", produces = [MediaType.TEXT_PLAIN])
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

    @Get("/illegal")
    suspend fun illegal(): Unit {
        throw IllegalArgumentException()
    }

    @Get("/illegalWithContext")
    suspend fun illegalWithContext(): String {
        return withContext(coroutineDispatcher) {
            throw IllegalArgumentException()
        }
    }

    @Status(HttpStatus.BAD_REQUEST)
    @Error(exception = IllegalArgumentException::class)
    @Produces(MediaType.TEXT_PLAIN)
    suspend fun onIllegalArgument(e: IllegalArgumentException): String {
        return "illegal.argument"
    }

    @Get("/callSuspendServiceWithRetries")
    suspend fun callSuspendServiceWithRetries(): String {
        return suspendService.delayedCalculation1()
    }

    @Get("/callSuspendServiceWithRetriesBlocked")
    fun callSuspendServiceWithRetriesBlocked(): String {
        // Bypass ContinuationArgumentBinder
        return runBlocking {
            suspendService.delayedCalculation2()
        }
    }
}