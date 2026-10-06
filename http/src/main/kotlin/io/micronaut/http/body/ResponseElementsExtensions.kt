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
package io.micronaut.http.body

import io.micronaut.core.annotation.Experimental
import io.micronaut.core.async.propagation.KotlinCoroutinePropagation
import io.micronaut.core.propagation.PropagatedContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import java.util.Optional
import kotlin.coroutines.CoroutineContext

/**
 * The elements of the flow as a [ResponseElements] body, without Reactive Streams: the flow is
 * collected in a coroutine as the server pulls the elements, one at a time, so it is suspended
 * while the client does not take them. The coroutine is cancelled when the server closes the
 * elements, e.g. when the client disconnects, and runs with the propagated context of the caller.
 *
 * ```
 * routes.GET("/books") { _, _ ->
 *     HttpResponse.ok(books.findAll().asResponseElements())
 * }
 * ```
 *
 * @param context The context of the coroutine, [Dispatchers.Default] if not given
 * @return The elements
 * @since 5.3.0
 */
@Experimental
fun <T : Any> Flow<T>.asResponseElements(context: CoroutineContext = Dispatchers.Default): ResponseElements<T> {
    val flow = this
    // rendezvous: the flow emits the next element only once the server took the previous one
    val channel = Channel<T>(Channel.RENDEZVOUS)
    val scope = CoroutineScope(KotlinCoroutinePropagation.addPropagatedContext(context + SupervisorJob(), PropagatedContext.getOrEmpty()))
    val producer = scope.launch(start = CoroutineStart.LAZY) {
        try {
            flow.collect { channel.send(it) }
            channel.close()
        } catch (e: CancellationException) {
            channel.cancel(e)
            throw e
        } catch (e: Throwable) {
            // an error too: the server waits for the next element
            channel.close(e)
        }
    }
    return ResponseElements.of({
        producer.start()
        scope.future {
            val result = channel.receiveCatching()
            val element = result.getOrNull()
            if (element == null) {
                result.exceptionOrNull()?.let { throw it }
            }
            Optional.ofNullable(element)
        }
    }, {
        scope.cancel()
    })
}
