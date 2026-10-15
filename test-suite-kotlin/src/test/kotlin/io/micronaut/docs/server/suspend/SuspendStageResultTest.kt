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
package io.micronaut.docs.server.suspend

import io.micronaut.aop.Around
import io.micronaut.aop.InterceptedMethod
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * A suspend method whose interceptor returns a [CompletionStage] gets its value or failure whether the stage is
 * already complete or completes later on another thread, with or without a dispatcher in the caller's context.
 */
@MicronautTest
@Property(name = "spec.name", value = "SuspendStageResultTest")
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class SuspendStageResultTest {

    @Inject
    lateinit var service: StageService

    @Test
    fun `a stage completed before the caller suspends returns its value`() = runBlocking {
        assertEquals("completed", service.value(CompletableFuture.completedFuture("completed")))
    }

    @Test
    fun `a stage completed with null for a unit method returns unit`() = runBlocking {
        assertSame(Unit, service.unit(CompletableFuture.completedFuture(null)))
    }

    @Test
    fun `a stage failed before the caller suspends throws its failure`() {
        val failure = IllegalStateException("failed")
        val thrown = assertThrows<IllegalStateException> {
            runBlocking { service.value(CompletableFuture.failedFuture(failure)) }
        }
        assertSame(failure, thrown)
    }

    @Test
    fun `a stage completed later resumes the caller on its dispatcher`() = runBlocking {
        val (value, thread) = withContext(Dispatchers.Default) {
            service.value(later { complete("later") }) to Thread.currentThread().name
        }
        assertEquals("later", value)
        assertTrue(thread.startsWith("DefaultDispatcher-worker"), thread)
    }

    @Test
    fun `a stage failed later throws the cause of a completion exception`() {
        val failure = IllegalStateException("failed later")
        val thrown = assertThrows<IllegalStateException> {
            runBlocking { service.value(later { completeExceptionally(CompletionException(failure)) }) }
        }
        assertSame(failure, thrown)
    }

    @Test
    fun `a completion exception without a cause is thrown as is`() {
        val failure = CompletionException("no cause", null)
        val thrown = assertThrows<CompletionException> {
            runBlocking { service.value(CompletableFuture.failedFuture(failure)) }
        }
        assertSame(failure, thrown)
    }

    @Test
    fun `a stage completed later resumes a caller without a dispatcher`() {
        val result = CompletableFuture<String>()
        val stage = CompletableFuture<String>()
        suspend { service.value(stage) }.startCoroutine(Continuation(EmptyCoroutineContext) { result.complete(it.getOrThrow()) })
        stage.completeAsync { "undispatched" }
        assertEquals("undispatched", result.get(10, TimeUnit.SECONDS))
    }

    private fun later(completion: CompletableFuture<String>.() -> Unit): CompletableFuture<String> {
        val stage = CompletableFuture<String>()
        CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS).execute { stage.completion() }
        return stage
    }

    @MustBeDocumented
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    @Around
    annotation class StageResult

    /**
     * Returns the stage passed as the first argument in place of proceeding.
     */
    @Requires(property = "spec.name", value = "SuspendStageResultTest")
    @InterceptorBean(StageResult::class)
    @Singleton
    class StageResultInterceptor : MethodInterceptor<Any, Any> {
        override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
            val interceptedMethod = InterceptedMethod.of(context)
            return interceptedMethod.handleResult(context.parameterValues[0] as CompletionStage<*>)
        }
    }

    @Requires(property = "spec.name", value = "SuspendStageResultTest")
    @Singleton
    @StageResult
    open class StageService {

        open suspend fun value(stage: CompletionStage<String>): String = error("intercepted")

        open suspend fun unit(stage: CompletionStage<Void?>): Unit = error("intercepted")
    }
}
