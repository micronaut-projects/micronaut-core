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

import io.micronaut.http.HttpResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

/**
 * The coroutine extensions of [SseEmitter]: the stream ends when the coroutine returns, fails with
 * what it throws or with a cancellation while the stream is open, and the coroutine is cancelled
 * when the stream closes.
 */
class SseEmitterExtensionsTest {

    @Test
    fun theStreamEndsWhenTheCoroutineReturns() {
        val events = FakeEmitter()
        events.launch(Dispatchers.Unconfined) {
            sendAwait(Event.of("a"))
            sendAwait("b")
        }.asCompletableFuture()
        assertTrue(events.keptOpen)
        assertEquals(listOf("a", "b"), events.sent.map { it.data })
        assertTrue(events.completed.get(10, TimeUnit.SECONDS))
        assertNull(events.failure)
    }

    @Test
    fun anExceptionOrAnErrorFailsTheStream() {
        for (thrown in listOf<Throwable>(IllegalStateException("exception"), AssertionError("error"))) {
            val events = FakeEmitter()
            events.launch(Dispatchers.Unconfined) {
                throw thrown
            }
            assertEquals(thrown, events.failure)
        }
    }

    @Test
    fun aCancellationWhileTheStreamIsOpenFailsIt() {
        val events = FakeEmitter()
        val job = events.launch {
            withTimeout(10) {
                delay(10_000)
            }
        }
        job.asCompletableFuture().handle { _, _ -> null }.get(10, TimeUnit.SECONDS)
        assertInstanceOf(TimeoutCancellationException::class.java, events.failure)
    }

    @Test
    fun theCoroutineIsCancelledWhenTheStreamCloses() {
        val events = FakeEmitter()
        val job = events.launch {
            awaitCancellation()
        }
        // e.g. the client disconnected
        events.closeStream(IllegalStateException("disconnected"))
        job.asCompletableFuture().handle { _, _ -> null }.get(10, TimeUnit.SECONDS)
        assertTrue(job.isCancelled)
        // the stream had ended: the cancellation does not fail it
        assertNull(events.failure)
    }

    private fun kotlinx.coroutines.Job.asCompletableFuture(): CompletableFuture<Unit> {
        val future = CompletableFuture<Unit>()
        invokeOnCompletion { error -> if (error == null) future.complete(Unit) else future.completeExceptionally(error) }
        return future
    }

    /**
     * Records what the extensions do with the emitter.
     */
    private class FakeEmitter : SseEmitter {
        val sent = mutableListOf<Event<*>>()
        val completed = CompletableFuture<Boolean>()
        private val callbacks = mutableListOf<Consumer<Throwable?>>()
        @Volatile var keptOpen = false
        @Volatile var failure: Throwable? = null
        @Volatile private var open = true

        fun closeStream(cause: Throwable?) {
            open = false
            callbacks.forEach { it.accept(cause) }
        }

        override fun send(event: Event<*>): CompletionStage<Void> {
            sent.add(event)
            return CompletableFuture.completedFuture(null)
        }

        override fun comment(comment: String): CompletionStage<Void> = CompletableFuture.completedFuture(null)

        override fun isWritable(): Boolean = open

        override fun isOpen(): Boolean = open

        override fun lastEventId(): Optional<String> = Optional.empty()

        override fun header(name: CharSequence, value: CharSequence): SseEmitter = this

        override fun respond(response: HttpResponse<*>) {
            open = false
        }

        override fun keepOpen(): SseEmitter {
            keptOpen = true
            return this
        }

        override fun heartbeat(period: Duration): SseEmitter = this

        override fun highWaterMark(bytes: Int): SseEmitter = this

        override fun onClose(callback: Consumer<Throwable?>): SseEmitter {
            callbacks.add(callback)
            return this
        }

        override fun complete() {
            if (open) {
                open = false
                completed.complete(true)
            }
        }

        override fun fail(cause: Throwable) {
            if (open) {
                open = false
                failure = cause
            }
        }
    }
}
