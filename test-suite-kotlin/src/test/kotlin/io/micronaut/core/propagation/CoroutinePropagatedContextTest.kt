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
package io.micronaut.core.propagation

import io.micronaut.core.async.propagation.KotlinCoroutinePropagation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.function.Supplier
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The context carried by a coroutine must be the current context whenever the coroutine runs, in both propagation
 * modes, and must not leak onto the threads that ran it.
 */
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
abstract class CoroutinePropagatedContextTest(private val mode: PropagatedContextConfiguration.Mode) {

    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()

    @BeforeEach
    fun setMode() {
        PropagatedContextConfiguration.set(mode)
    }

    @AfterEach
    fun cleanup() {
        PropagatedContextConfiguration.reset()
        dispatcher.close()
    }

    @Test
    fun `the context is current across suspensions and dispatcher switches`() {
        val context = PropagatedContext.empty().plus(Name("coroutine"))
        val seen = runBlocking(KotlinCoroutinePropagation.addPropagatedContext(EmptyCoroutineContext, context)) {
            val names = mutableListOf(currentName())
            delay(10)
            names += currentName()
            names += withContext(Dispatchers.Default) {
                delay(10)
                currentName()
            }
            names += withContext(dispatcher) {
                yield()
                currentName()
            }
            names += coroutineScope {
                (1..3).map { async(Dispatchers.IO) { delay(5); currentName() } }.awaitAll()
            }
            names += currentName()
            names
        }
        assertEquals(List(8) { "coroutine" }, seen)
        assertFalse(PropagatedContext.find().isPresent)
        assertNoContextOn(dispatcher)
    }

    @Test
    fun `thread elements are updated while the coroutine runs and restored afterwards`() {
        val context = PropagatedContext.empty().plus(ThreadName("coroutine"))
        val seen = runBlocking(KotlinCoroutinePropagation.addPropagatedContext(dispatcher, context)) {
            val before = THREAD_NAME.get()
            delay(10)
            val afterDelay = THREAD_NAME.get()
            val onDefault = withContext(Dispatchers.Default) { THREAD_NAME.get() }
            listOf(before, afterDelay, onDefault, THREAD_NAME.get())
        }
        assertEquals(List(4) { "coroutine" }, seen)
        assertNull(THREAD_NAME.get())
        assertNull(withContextOn(dispatcher) { THREAD_NAME.get() })
    }

    @Test
    fun `a context propagated inside the coroutine takes precedence for its extent`() {
        val context = PropagatedContext.empty().plus(Name("coroutine"))
        val seen = runBlocking(KotlinCoroutinePropagation.addPropagatedContext(dispatcher, context)) {
            val nested = PropagatedContext.get().plus(Name("nested")).propagate(Supplier { currentName() })
            val outer = PropagatedContext.empty().propagate(Supplier { nameOf(PropagatedContext.getOrEmpty()) })
            listOf(nested, outer, currentName())
        }
        assertEquals(listOf("nested", NONE, "coroutine"), seen)
    }

    @Test
    fun `the coroutine's context takes precedence over the context it is started from`() {
        val outer = PropagatedContext.empty().plus(Name("outer"))
        val inner = PropagatedContext.empty().plus(Name("inner"))
        val seen = outer.propagate(Supplier {
            val inCoroutine = runBlocking(KotlinCoroutinePropagation.addPropagatedContext(EmptyCoroutineContext, inner)) {
                listOf(currentName(), withContext(Dispatchers.Default) { currentName() })
            }
            inCoroutine + currentName()
        })
        assertEquals(listOf("inner", "inner", "outer"), seen)
        assertFalse(PropagatedContext.find().isPresent)
    }

    @Test
    fun `the coroutine's context is the one bound by the callbacks it is propagated with`() {
        val context = PropagatedContext.empty().plus(Name("coroutine"))
        runBlocking(KotlinCoroutinePropagation.addPropagatedContext(dispatcher, context)) {
            assertSame(context, PropagatedContext.get())
            // Already in scope, the callback runs without a new binding
            assertSame(context, context.propagate(Supplier { PropagatedContext.get() }))
            val wrapped = PropagatedContext.wrapCurrent(Supplier { currentName() })
            assertEquals("coroutine", Executors.newSingleThreadExecutor().let {
                try {
                    it.submit(Callable { wrapped.get() }).get()
                } finally {
                    it.shutdown()
                }
            })
        }
    }

    private fun assertNoContextOn(dispatcher: CoroutineDispatcher) {
        assertFalse(withContextOn(dispatcher) { PropagatedContext.find().isPresent })
    }

    private fun <T> withContextOn(dispatcher: CoroutineDispatcher, block: () -> T): T =
        runBlocking { withContext(dispatcher) { block() } }

    private fun currentName(): String = nameOf(PropagatedContext.get())

    private fun nameOf(context: PropagatedContext): String = context.find(Name::class.java).map { it.name }.orElse(NONE)

    private data class Name(val name: String) : PropagatedContextElement

    private data class ThreadName(val name: String) : ThreadPropagatedContextElement<String> {
        override fun updateThreadContext(): String? {
            val previous: String? = THREAD_NAME.get()
            THREAD_NAME.set(name)
            return previous
        }

        override fun restoreThreadContext(oldState: String?) {
            if (oldState == null) {
                THREAD_NAME.remove()
            } else {
                THREAD_NAME.set(oldState)
            }
        }
    }

    companion object {
        private const val NONE = "none"
        private val THREAD_NAME = ThreadLocal<String>()
    }
}

class ThreadLocalCoroutinePropagatedContextTest : CoroutinePropagatedContextTest(PropagatedContextConfiguration.Mode.THREAD_LOCAL)

class ScopedValueCoroutinePropagatedContextTest : CoroutinePropagatedContextTest(PropagatedContextConfiguration.Mode.SCOPED_VALUE)
