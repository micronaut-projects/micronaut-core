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
package io.micronaut.http.bind.binders

import io.micronaut.core.bind.ArgumentBinder
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.reflect.ClassUtils
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.context.ServerRequestContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.reactor.ReactorContext
import reactor.util.context.ContextView
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.jvm.internal.CoroutineStackFrame

class ContinuationArgumentBinder : TypedRequestArgumentBinder<Continuation<*>> {
    override fun bind(
        context: ArgumentConversionContext<Continuation<*>>?,
        source: HttpRequest<*>
    ): ArgumentBinder.BindingResult<Continuation<*>> {
        val cc = CustomContinuation()
        source.setAttribute(CONTINUATION_ARGUMENT_ATTRIBUTE_KEY, cc)
        return ArgumentBinder.BindingResult { Optional.of(cc) }
    }

    override fun argumentType(): Argument<Continuation<*>> = Argument.of(Continuation::class.java)

    companion object {

        private val reactorContextPresent: Boolean = ClassUtils.isPresent("kotlinx.coroutines.reactor.ReactorContext", null);

        @JvmStatic
        fun setupCoroutineContext(source: HttpRequest<*>, contextView: ContextView) {
            val customContinuation = source.getAttribute(CONTINUATION_ARGUMENT_ATTRIBUTE_KEY, CustomContinuation::class.java).orElse(null)
            if (customContinuation != null) {
                var coroutineContext: CoroutineContext = Dispatchers.Default + ServerRequestScopeHandler(source)
                if (reactorContextPresent) {
                    coroutineContext += propagateReactorContext(contextView)
                }
                customContinuation.context.delegatingCoroutineContext = coroutineContext
            }
        }

        private fun propagateReactorContext(contextView: ContextView) : CoroutineContext {
            return if (contextView.isEmpty) {
                EmptyCoroutineContext
            } else {
                ReactorContext(contextView)
            }
        }

        @JvmStatic
        fun extractContinuationCompletableFutureSupplier(source: HttpRequest<*>): Supplier<CompletableFuture<*>>? {
            return source.getAttribute(CONTINUATION_ARGUMENT_ATTRIBUTE_KEY, CustomContinuation::class.java).orElse(null)
        }

        private const val CONTINUATION_ARGUMENT_ATTRIBUTE_KEY = "__continuation__"
    }
}

class DelegatingCoroutineContext : CoroutineContext {

    var delegatingCoroutineContext: CoroutineContext? = null

    override fun <R> fold(initial: R, operation: (R, CoroutineContext.Element) -> R): R {
        return delegatingCoroutineContext!!.fold(initial, operation)
    }

    override fun <E : CoroutineContext.Element> get(key: CoroutineContext.Key<E>): E? {
        return delegatingCoroutineContext!![key]
    }

    override fun minusKey(key: CoroutineContext.Key<*>): CoroutineContext {
        return delegatingCoroutineContext!!.minusKey(key)
    }

}

private class CustomContinuation: Continuation<Any?>, CoroutineStackFrame, Supplier<CompletableFuture<*>> {

    var coroutineContext = DelegatingCoroutineContext()

    private val completableFuture = CompletableFuture<Any?>()

    override fun get(): CompletableFuture<Any?> = completableFuture

    override var context: DelegatingCoroutineContext = coroutineContext

    override fun resumeWith(result: Result<Any?>) {
        if (result.isSuccess) {
            completableFuture.complete(result.getOrNull())
        } else {
            completableFuture.completeExceptionally(result.exceptionOrNull())
        }
    }

    override val callerFrame: CoroutineStackFrame? = null
    override fun getStackTraceElement(): StackTraceElement? = null
}

private class ServerRequestScopeHandler(
        private val httpRequest: HttpRequest<*>
) : ThreadContextElement<HttpRequest<*>?> {

    companion object Key : CoroutineContext.Key<ServerRequestScopeHandler>

    override val key: CoroutineContext.Key<ServerRequestScopeHandler>
        get() = Key

    override fun updateThreadContext(context: CoroutineContext): HttpRequest<*>? {
        val previous = ServerRequestContext.currentRequest<HttpRequest<*>>().orElse(null)
        ServerRequestContext.set(httpRequest)
        return previous
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: HttpRequest<*>?) = ServerRequestContext.set(oldState)
}