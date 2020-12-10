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
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.context.ServerRequestContext
import kotlinx.coroutines.ThreadContextElement
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.jvm.internal.CoroutineStackFrame

class ContinuationArgumentBinder : TypedRequestArgumentBinder<Continuation<*>> {
    override fun bind(
        context: ArgumentConversionContext<Continuation<*>>?,
        source: HttpRequest<*>
    ): ArgumentBinder.BindingResult<Continuation<*>> =
        with(CustomContinuation(source)) {
            source.setAttribute(CONTINUATION_ARGUMENT_ATTRIBUTE_KEY, this)
            ArgumentBinder.BindingResult { Optional.of(this) }
        }

    override fun argumentType(): Argument<Continuation<*>> = Argument.of(Continuation::class.java)

    @Deprecated("Deprecated in v2.1. Will be removed in v3", replaceWith = ReplaceWith("superTypes()"))
    override fun supportsSuperTypes(): Boolean = false

    companion object {
        @JvmStatic
        fun extractContinuationCompletableFutureSupplier(source: HttpRequest<*>): Supplier<CompletableFuture<*>>? =
            source.getAttribute(CONTINUATION_ARGUMENT_ATTRIBUTE_KEY, CustomContinuation::class.java).orElse(null)

        private const val CONTINUATION_ARGUMENT_ATTRIBUTE_KEY = "__continuation__"
    }
}

private class CustomContinuation(
    httpRequest: HttpRequest<*>
) : Continuation<Any?>, CoroutineStackFrame, Supplier<CompletableFuture<*>> {

    private val serverRequestScopeHandler = ServerRequestScopeHandler(httpRequest)
    private val completableFuture = CompletableFuture<Any?>()

    override fun get(): CompletableFuture<Any?> = completableFuture

    override val context: CoroutineContext =
        serverRequestScopeHandler

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
) : ThreadContextElement<Unit> {

    companion object Key : CoroutineContext.Key<ServerRequestScopeHandler>

    override val key: CoroutineContext.Key<ServerRequestScopeHandler>
        get() = Key

    override fun updateThreadContext(context: CoroutineContext) =
        ServerRequestContext.set(httpRequest)

    override fun restoreThreadContext(context: CoroutineContext, oldState: Unit) =
        ServerRequestContext.set(null)
}
