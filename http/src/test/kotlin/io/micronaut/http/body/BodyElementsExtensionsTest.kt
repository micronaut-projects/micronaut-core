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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * [asBodyElements]: a flow pulled one element at a time, failing with what the flow throws, and
 * cancelled when the elements are closed.
 */
class BodyElementsExtensionsTest {

    @Test
    fun theElementsOfAFlow() {
        val elements = flowOf(1, 2, 3).asBodyElements()
        val read = mutableListOf<Int>()
        elements.forEach { element ->
            read.add(element)
            java.util.concurrent.CompletableFuture.completedFuture(null)
        }.toCompletableFuture().get(10, TimeUnit.SECONDS)
        assertEquals(listOf(1, 2, 3), read)
        assertEquals(Optional.empty<Int>(), elements.next().toCompletableFuture().get(10, TimeUnit.SECONDS))
    }

    @Test
    fun aFailureOfTheFlowFailsTheRead() {
        for (thrown in listOf<Throwable>(IllegalStateException("exception"), AssertionError("error"))) {
            val elements = flow<Int> { throw thrown }.asBodyElements()
            val failure = assertThrows(ExecutionException::class.java) {
                elements.next().toCompletableFuture().get(10, TimeUnit.SECONDS)
            }
            assertEquals(thrown.message, failure.cause!!.message)
        }
    }

    @Test
    fun closingCancelsTheFlow() {
        val cancelled = CompletableDeferred<Throwable?>()
        val elements = flow {
            var n = 0
            while (true) {
                emit(n++)
            }
        }.onCompletion { cancelled.complete(it) }.asBodyElements()
        assertEquals(Optional.of(0), elements.next().toCompletableFuture().get(10, TimeUnit.SECONDS))
        elements.close()
        val cause = java.util.concurrent.CompletableFuture<Throwable?>()
        cancelled.invokeOnCompletion { cause.complete(cancelled.getCompleted()) }
        assertTrue(cause.get(10, TimeUnit.SECONDS) is kotlinx.coroutines.CancellationException)
    }

    @Test
    fun anElementTheServerDoesNotTakeIsClosed() {
        assertUndeliveredElementIsClosed(failingClose = false)
    }

    @Test
    fun anElementThatFailsToCloseIsDiscardedQuietly() {
        // the failure is logged: the stream of the flow is not affected
        assertUndeliveredElementIsClosed(failingClose = true)
    }

    private fun assertUndeliveredElementIsClosed(failingClose: Boolean) {
        val emitted = mutableListOf<Element>()
        val second = CompletableDeferred<Unit>()
        val elements = flow {
            var n = 0
            while (true) {
                val element = Element(failingClose)
                synchronized(emitted) { emitted.add(element) }
                if (n++ == 1) {
                    second.complete(Unit)
                }
                emit(element)
            }
        }.asBodyElements()
        val first = elements.next().toCompletableFuture().get(10, TimeUnit.SECONDS).get()
        // the flow offers the second element, which nobody takes
        val offered = java.util.concurrent.CompletableFuture<Unit>()
        second.invokeOnCompletion { offered.complete(Unit) }
        offered.get(10, TimeUnit.SECONDS)
        elements.close()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!synchronized(emitted) { emitted[1].closed } && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
        assertTrue(synchronized(emitted) { emitted[1].closed }, "the undelivered element is closed")
        assertTrue(!first.closed, "the delivered element belongs to the caller")
    }

    class Element(private val failingClose: Boolean) : AutoCloseable {
        @Volatile
        var closed = false

        override fun close() {
            closed = true
            if (failingClose) {
                throw java.io.IOException("cannot close")
            }
        }
    }
}
