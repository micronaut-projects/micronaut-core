/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.aop.util

import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.ThreadPropagatedContextElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class KotlinInterceptedMethodHelperTest {

    @Test
    fun handleResultReturnsValueWithoutContext() {
        val result = runSuspend {
            KotlinInterceptedMethodHelper.handleResult(CompletableFuture.completedFuture("ok"), false)
        }

        assertEquals("ok", result)
    }

    @Test
    fun handleResultUnwrapsCompletionException() {
        val future = CompletableFuture<Any?>().also {
            it.completeExceptionally(CompletionException(IllegalStateException("boom")))
        }

        val exception = assertThrows(IllegalStateException::class.java) {
            runSuspend {
                KotlinInterceptedMethodHelper.handleResult(future, false)
            }
        }

        assertEquals("boom", exception.message)
    }

    @Test
    fun handleResultResumesWithPropagatedContext() {
        val marker = ThreadLocal.withInitial { 0 }
        val future = CompletableFuture<String>()

        val resumedMarker = PropagatedContext.getOrEmpty()
            .plus(ThreadLocalMarker(marker, 42))
            .propagate {
                val completingThread = Thread {
                    Thread.sleep(20)
                    future.complete("done")
                }
                completingThread.start()
                try {
                    runSuspend {
                        KotlinInterceptedMethodHelper.handleResult(future, false)
                        marker.get()
                    }
                } finally {
                    completingThread.join()
                }
            }

        assertEquals(42, resumedMarker)
        assertEquals(0, marker.get())
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        val result = CompletableFuture<T>()
        block.startCoroutine(object : Continuation<T> {
            override val context: CoroutineContext = EmptyCoroutineContext

            override fun resumeWith(resumeResult: Result<T>) {
                resumeResult
                    .onSuccess(result::complete)
                    .onFailure(result::completeExceptionally)
            }
        })
        return try {
            result.join()
        } catch (e: CompletionException) {
            throw (e.cause ?: e)
        }
    }

    private data class ThreadLocalMarker(
        private val threadLocal: ThreadLocal<Int>,
        private val value: Int,
    ) : ThreadPropagatedContextElement<Int> {
        override fun updateThreadContext(): Int {
            val old = threadLocal.get()
            threadLocal.set(value)
            return old
        }

        override fun restoreThreadContext(oldState: Int?) {
            threadLocal.set(oldState)
        }
    }
}
