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
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class ContinuationArgumentBinder: TypedRequestArgumentBinder<Continuation<*>> {
    override fun bind(context: ArgumentConversionContext<Continuation<*>>?, source: HttpRequest<*>): ArgumentBinder.BindingResult<Continuation<*>> =
        with (CustomContinuation()) {
            source.setAttribute(CONTINUATION_ARGUMENT_ATTRIBUTE_KEY, this)
            ArgumentBinder.BindingResult { Optional.of(this as Continuation<*>) }
        }

    override fun argumentType(): Argument<Continuation<*>> = Argument.of(Continuation::class.java)

    override fun supportsSuperTypes(): Boolean = false

    companion object {
        @JvmStatic
        fun extractContinuationCompletableFutureSupplier(source: HttpRequest<*>): Supplier<CompletableFuture<*>> =
            source.getAttribute(CONTINUATION_ARGUMENT_ATTRIBUTE_KEY).orElse(null) as CustomContinuation

        private const val CONTINUATION_ARGUMENT_ATTRIBUTE_KEY = "__continuation__"
    }
}

private class CustomContinuation: Continuation<Any>, Supplier<CompletableFuture<*>> {
    private val completableFuture = CompletableFuture<Any>()

    override fun get(): CompletableFuture<*> = completableFuture

    override val context: CoroutineContext
        get() = EmptyCoroutineContext

    override fun resumeWith(result: Result<Any>) {
        if (result.isSuccess) {
            completableFuture.complete(result.getOrNull())
        } else {
            completableFuture.completeExceptionally(result.exceptionOrNull())
        }
    }
}