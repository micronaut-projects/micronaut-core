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

import io.micronaut.core.annotation.Experimental
import io.micronaut.core.annotation.Internal
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.jvm.internal.CoroutineStackFrame

/**
 * Continuation represented as CompletableFuture
 *
 * @author Denis Stepanov
 * @since 2.1.0
 */
@Internal
@Experimental
class CompletableFutureContinuation(
    private val continuation: Continuation<Any?>
) : Continuation<Any?>, CoroutineStackFrame {

    var completableFuture = CompletableFuture<Any?>()

    override val callerFrame: CoroutineStackFrame?
        get() = continuation as? CoroutineStackFrame

    override fun getStackTraceElement(): StackTraceElement? = null

    override val context: CoroutineContext
        get() = continuation.context

    override fun resumeWith(result: Result<Any?>) {
        if (result.isSuccess) {
            completableFuture.complete(result.getOrNull())
        } else {
            completableFuture.completeExceptionally(result.exceptionOrNull())
        }
    }

    companion object {
        fun completeSuccess(continuation: Continuation<Any?>, result: Any?) {
            continuation.resumeWith(Result.success(result))
        }

        fun completeExceptionally(continuation: Continuation<Any?>, exception: Throwable) {
            continuation.resumeWith(Result.failure(exception))
        }
    }
}