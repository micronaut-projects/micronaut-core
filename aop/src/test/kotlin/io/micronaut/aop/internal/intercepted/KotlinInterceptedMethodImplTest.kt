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
package io.micronaut.aop.internal.intercepted

import io.micronaut.aop.MethodInvocationContext
import io.micronaut.core.async.propagation.KotlinCoroutinePropagation
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.core.type.Argument
import io.micronaut.inject.ExecutableMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.lang.reflect.Proxy
import java.util.function.Supplier
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/**
 * The propagated context current when a suspend method is intercepted applies to the intercepted method only:
 * the caller resumes with its own coroutine context.
 */
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class KotlinInterceptedMethodImplTest {

    @Test
    fun `a caller without a propagated context does not resume in the interceptor's context`() {
        val seen = runBlocking {
            val names = mutableListOf(interceptWithName { yield(); currentName() }, currentName())
            yield()
            names += currentName()
            names
        }
        assertEquals(listOf(INTERCEPTED, NONE, NONE), seen)
        assertEquals(NONE, currentName())
    }

    @Test
    fun `a caller without a propagated context does not resume in the interceptor's context after a dispatcher switch`() {
        val seen = runBlocking {
            val inside = interceptWithName {
                withContext(Dispatchers.Default) { yield() }
                currentName()
            }
            listOf(inside, currentName())
        }
        assertEquals(listOf(INTERCEPTED, NONE), seen)
    }

    @Test
    fun `a caller resumes in its own propagated context`() {
        val caller = PropagatedContext.empty().plus(Name(CALLER))
        val seen = runBlocking(KotlinCoroutinePropagation.addPropagatedContext(EmptyCoroutineContext, caller)) {
            val names = mutableListOf(currentName(), interceptWithName { yield(); currentName() }, currentName())
            yield()
            names += currentName()
            names
        }
        assertEquals(listOf(CALLER, INTERCEPTED, CALLER, CALLER), seen)
    }

    @Test
    fun `a method intercepted without a propagated context runs in the caller's context`() {
        val seen = runBlocking {
            val inside = intercept { method -> method.interceptResultAsCompletionStage() }.invoke { yield(); currentName() }
            listOf(inside, currentName())
        }
        assertEquals(listOf(NONE, NONE), seen)
    }

    private suspend fun interceptWithName(body: suspend () -> Any?): Any? =
        intercept { method ->
            PropagatedContext.getOrEmpty().plus(Name(INTERCEPTED)).propagate(Supplier {
                method.interceptResultAsCompletionStage()
            })
        }.invoke(body)

    /**
     * Intercepts the given suspend body as an interceptor would: [proceed] returns the stage of the intercepted
     * method, which is then handed to [KotlinInterceptedMethodImpl.handleResult] outside of any scope it opened.
     */
    private fun intercept(proceed: (KotlinInterceptedMethodImpl) -> Any?): suspend (suspend () -> Any?) -> Any? = { body ->
        suspendCoroutineUninterceptedOrReturn { continuation ->
            val parameterValues = arrayOf<Any?>(continuation)
            val method = KotlinInterceptedMethodImpl.of(invocationContext(parameterValues, body))!!
            method.handleResult(proceed(method))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun invocationContext(parameterValues: Array<Any?>, body: suspend () -> Any?): MethodInvocationContext<*, *> {
        val executableMethod = proxy(ExecutableMethod::class.java) { name ->
            if (name == "isSuspend") true else null
        }
        val arguments = arrayOf<Argument<*>>(Argument.of(Continuation::class.java, Argument.OBJECT_ARGUMENT))
        return proxy(MethodInvocationContext::class.java) { name ->
            when (name) {
                "isSuspend" -> true
                "getExecutableMethod" -> executableMethod
                "getParameterValues" -> parameterValues
                "getArguments" -> arguments
                "proceed" -> body.startCoroutineUninterceptedOrReturn(parameterValues[0] as Continuation<Any?>)
                else -> null
            }
        }
    }

    private fun <T> proxy(type: Class<T>, answer: (String) -> Any?): T =
        type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            answer(method.name) ?: throw UnsupportedOperationException(method.name)
        })

    private data class Name(val name: String) : PropagatedContextElement

    companion object {
        private const val INTERCEPTED = "intercepted"
        private const val CALLER = "caller"
        private const val NONE = "none"

        private fun currentName(): String =
            PropagatedContext.getOrEmpty().find(Name::class.java).map { it.name }.orElse(NONE)
    }
}
