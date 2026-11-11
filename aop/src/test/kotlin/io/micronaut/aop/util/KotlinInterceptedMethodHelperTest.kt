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
package io.micronaut.aop.util

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.startCoroutine

/**
 * A suspend caller awaiting a [CompletionStage] gets its value or failure whether the stage is already complete or
 * completes later on another thread, and in the latter case resumes on the dispatcher of its context, also when its
 * continuation is a [DelegatingContextContinuation].
 */
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class KotlinInterceptedMethodHelperTest {

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "caller-dispatcher") }
    private val dispatcher = executor.asCoroutineDispatcher()

    @AfterEach
    fun shutdown() {
        executor.shutdownNow()
    }

    @Test
    fun `a stage completed before the caller suspends returns its value`() = runBlocking {
        assertEquals("completed", await(CompletableFuture.completedFuture("completed")))
    }

    @Test
    fun `a stage completed with null for a unit method returns unit`() = runBlocking {
        assertSame(Unit, await(CompletableFuture.completedFuture(null), isUnitValueType = true))
    }

    @Test
    fun `a stage failed before the caller suspends throws its failure`() {
        val failure = IllegalStateException("failed")
        assertSame(failure, assertThrows<IllegalStateException> {
            runBlocking { await(CompletableFuture.failedFuture<Any?>(failure)) }
        })
    }

    @Test
    fun `a completion exception without a cause is thrown as is`() {
        val failure = CompletionException("no cause", null)
        assertSame(failure, assertThrows<CompletionException> {
            runBlocking { await(CompletableFuture.failedFuture<Any?>(failure)) }
        })
    }

    @Test
    fun `a stage failed later throws the cause of a completion exception`() {
        val failure = IllegalStateException("failed later")
        assertSame(failure, assertThrows<IllegalStateException> {
            runBlocking { await(later { completeExceptionally(CompletionException(failure)) }) }
        })
    }

    @Test
    fun `a stage completed later resumes the caller on its dispatcher`() = runBlocking {
        val (value, thread) = withContext(dispatcher) {
            await(later { complete("later") }) to Thread.currentThread().name
        }
        assertEquals("later", value)
        assertTrue(thread.startsWith("caller-dispatcher"), thread)
    }

    @Test
    fun `a stage completed later resumes a delegating context continuation on its dispatcher with its context`() = runBlocking {
        val (value, thread, name) = withContext(dispatcher) {
            val value = suspendCoroutineUninterceptedOrReturn { continuation ->
                KotlinInterceptedMethodHelper.handleResult(
                    later { complete("delegated") },
                    false,
                    DelegatingContextContinuation(continuation, continuation.context + CoroutineName("delegated"))
                )
            }
            Triple(value, Thread.currentThread().name, currentCoroutineContext()[CoroutineName]?.name)
        }
        assertEquals("delegated", value)
        assertTrue(thread.startsWith("caller-dispatcher"), thread)
        // The caller's own context is unchanged once it resumes
        assertEquals(null, name)
    }

    @Test
    fun `a stage completed later resumes a caller without a dispatcher`() {
        val result = CompletableFuture<Any?>()
        val stage = CompletableFuture<Any?>()
        suspend { await(stage) }.startCoroutine(Continuation(EmptyCoroutineContext) { result.complete(it.getOrThrow()) })
        stage.completeAsync { "undispatched" }
        assertEquals("undispatched", result.get(10, TimeUnit.SECONDS))
    }

    private suspend fun await(stage: CompletionStage<*>, isUnitValueType: Boolean = false): Any? =
        suspendCoroutineUninterceptedOrReturn { KotlinInterceptedMethodHelper.handleResult(stage, isUnitValueType, it) }

    private fun later(completion: CompletableFuture<Any?>.() -> Unit): CompletableFuture<Any?> {
        val stage = CompletableFuture<Any?>()
        CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS).execute { stage.completion() }
        return stage
    }
}
