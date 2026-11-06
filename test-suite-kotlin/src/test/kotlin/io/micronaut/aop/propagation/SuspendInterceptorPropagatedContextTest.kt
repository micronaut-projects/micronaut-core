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
package io.micronaut.aop.propagation

import io.micronaut.aop.Around
import io.micronaut.aop.InterceptedMethod
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.propagation.KotlinCoroutinePropagation
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextConfiguration
import io.micronaut.core.propagation.PropagatedContextElement
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.function.Supplier
import kotlin.coroutines.EmptyCoroutineContext

/**
 * An interceptor that switches the propagated context for a suspend method must only affect the intercepted
 * method: the caller must resume with its own context, also when the caller has no propagated context at all.
 */
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
abstract class SuspendInterceptorPropagatedContextTest(private val mode: PropagatedContextConfiguration.Mode) {

    private lateinit var applicationContext: ApplicationContext
    private lateinit var service: SwitchedContextService

    @BeforeEach
    fun setup() {
        PropagatedContextConfiguration.set(mode)
        applicationContext = ApplicationContext.run(mapOf("spec.name" to SPEC_NAME))
        service = applicationContext.getBean(SwitchedContextService::class.java)
    }

    @AfterEach
    fun cleanup() {
        applicationContext.close()
        PropagatedContextConfiguration.reset()
    }

    @Test
    fun `the caller without a propagated context does not resume in the interceptor's context`() {
        val seen = runBlocking {
            val names = mutableListOf(service.name(), currentName())
            yield()
            names += currentName()
            names
        }
        assertEquals(listOf(INTERCEPTED, NONE, NONE), seen)
        assertEquals(NONE, currentName())
    }

    @Test
    fun `the caller without a propagated context does not resume in the interceptor's context after a dispatcher switch`() {
        val seen = runBlocking {
            val names = mutableListOf(service.nameOnAnotherDispatcher(), currentName())
            yield()
            names += currentName()
            names
        }
        assertEquals(listOf(INTERCEPTED, NONE, NONE), seen)
        assertEquals(NONE, currentName())
    }

    @Test
    fun `the caller resumes in its own propagated context`() {
        val caller = PropagatedContext.empty().plus(Name(CALLER))
        val seen = runBlocking(KotlinCoroutinePropagation.addPropagatedContext(EmptyCoroutineContext, caller)) {
            val names = mutableListOf(currentName(), service.name(), currentName(), service.nameOnAnotherDispatcher(), currentName())
            yield()
            names += currentName()
            names
        }
        assertEquals(listOf(CALLER, INTERCEPTED, CALLER, INTERCEPTED, CALLER, CALLER), seen)
        assertEquals(NONE, currentName())
    }

    @Around
    @MustBeDocumented
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    annotation class SwitchContext

    @Requires(property = "spec.name", value = SPEC_NAME)
    @InterceptorBean(SwitchContext::class)
    @Singleton
    class SwitchContextInterceptor : MethodInterceptor<Any, Any> {
        override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
            val interceptedMethod = InterceptedMethod.of(context, ConversionService.SHARED)
            return try {
                if (interceptedMethod.resultType() == InterceptedMethod.ResultType.COMPLETION_STAGE) {
                    val stage = PropagatedContext.getOrEmpty().plus(Name(INTERCEPTED)).propagate(Supplier {
                        interceptedMethod.interceptResultAsCompletionStage()
                    })
                    interceptedMethod.handleResult(stage)
                } else {
                    context.proceed()
                }
            } catch (e: Exception) {
                interceptedMethod.handleException<Exception>(e)
            }
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @SwitchContext
    @Singleton
    open class SwitchedContextService {
        open suspend fun name(): String {
            yield()
            return currentName()
        }

        open suspend fun nameOnAnotherDispatcher(): String {
            withContext(Dispatchers.Default) {
                yield()
            }
            return currentName()
        }
    }

    private data class Name(val name: String) : PropagatedContextElement

    companion object {
        private const val SPEC_NAME = "SuspendInterceptorPropagatedContextTest"
        private const val INTERCEPTED = "intercepted"
        private const val CALLER = "caller"
        private const val NONE = "none"

        private fun currentName(): String =
            PropagatedContext.getOrEmpty().find(Name::class.java).map { it.name }.orElse(NONE)
    }
}

class ThreadLocalSuspendInterceptorPropagatedContextTest : SuspendInterceptorPropagatedContextTest(PropagatedContextConfiguration.Mode.THREAD_LOCAL)

class ScopedValueSuspendInterceptorPropagatedContextTest : SuspendInterceptorPropagatedContextTest(PropagatedContextConfiguration.Mode.SCOPED_VALUE)
