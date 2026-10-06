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
package io.micronaut.http.sse

import io.micronaut.core.annotation.Experimental
import io.micronaut.core.async.propagation.KotlinCoroutinePropagation
import io.micronaut.core.propagation.PropagatedContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Send an event, and suspend until its stage completes: [SseEmitter.sendAndAwait] without
 * blocking the thread.
 *
 * @param event The event
 * @since 5.3.0
 */
@Experimental
suspend fun SseEmitter.sendAwait(event: Event<*>) {
    send(event).await()
}

/**
 * Send an event with the given data, and suspend until its stage completes: [SseEmitter.sendAndAwait]
 * without blocking the thread.
 *
 * @param data The data of the event, or an [Event]
 * @since 5.3.0
 */
@Experimental
suspend fun SseEmitter.sendAwait(data: Any) {
    send(data).await()
}

/**
 * Run the stream in a coroutine: the stream is kept open while [block] runs, completes when it
 * returns, and fails with the exception it throws. The coroutine is cancelled when the stream
 * closes, e.g. when the client disconnects, and runs with the propagated context of the caller,
 * the route handler.
 *
 * ```
 * routes.GET("/ticks").sse { _, _, events ->
 *     events.launch {
 *         repeat(10) {
 *             sendAwait("tick $it")
 *             delay(1000)
 *         }
 *     }
 * }
 * ```
 *
 * @param context The context of the coroutine, [Dispatchers.Default] if not given
 * @param block   Sends the events
 * @return The job of the coroutine
 * @since 5.3.0
 */
@Experimental
fun SseEmitter.launch(context: CoroutineContext = Dispatchers.Default, block: suspend SseEmitter.() -> Unit): Job {
    keepOpen()
    val emitter = this
    val scope = CoroutineScope(KotlinCoroutinePropagation.addPropagatedContext(context, PropagatedContext.getOrEmpty()))
    val job = scope.launch {
        try {
            emitter.block()
            emitter.complete()
        } catch (e: CancellationException) {
            // the stream closed, or the coroutine was cancelled: end the stream if it is open
            emitter.complete()
            throw e
        } catch (e: Exception) {
            emitter.fail(e)
        }
    }
    onClose { job.cancel() }
    return job
}
