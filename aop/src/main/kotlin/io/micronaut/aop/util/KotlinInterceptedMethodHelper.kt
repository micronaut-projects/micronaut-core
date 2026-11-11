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
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

/**
 * A helper utility class for intercepting Kotlin suspend methods as a value of [CompletionStage].
 *
 * @author Benedikt Hunger
 * @since 3.5.0
 */
@Internal
@Experimental
internal object KotlinInterceptedMethodHelper {

    /**
     * Suspends the caller until the given stage completes.
     *
     * Unlike `suspendCoroutine`, which intercepts the continuation through `intercepted()`, the continuation is
     * intercepted explicitly with the [ContinuationInterceptor] of its context. `intercepted()` only works for
     * compiler generated continuations, so a continuation with an updated context, such as a
     * [DelegatingContextContinuation], would otherwise resume inline on whichever thread completes the stage,
     * bypassing the caller's dispatcher and the thread context elements of its context.
     *
     * @param result The stage to await
     * @param isUnitValueType Whether the suspend method returns [Unit]
     * @param continuation The caller's continuation
     * @return The value if the stage is already complete, otherwise [COROUTINE_SUSPENDED]
     */
    @JvmStatic
    fun handleResult(result: CompletionStage<*>, isUnitValueType: Boolean, continuation: Continuation<Any?>): Any? {
        val resumption = StageResumption(continuation)
        result.whenComplete { value: Any?, throwable: Throwable? ->
            if (throwable == null) {
                resumption.resume(Result.success(value ?: if (isUnitValueType) Unit else null))
            } else {
                val exception = if (throwable is CompletionException) {
                    throwable.cause ?: throwable
                } else {
                    throwable
                }
                resumption.resume(Result.failure(exception))
            }
        }
        return resumption.getOrSuspend()
    }

    /**
     * Resumes the continuation through its dispatcher, or hands the result back to the caller when the stage
     * completes before the caller suspends, as `SafeContinuation` does.
     */
    private class StageResumption(private val continuation: Continuation<Any?>) : AtomicReference<Any?>(UNDECIDED) {

        fun resume(result: Result<Any?>) {
            if (compareAndSet(UNDECIDED, result)) {
                return
            }
            val interceptor = continuation.context[ContinuationInterceptor]
            val intercepted = interceptor?.interceptContinuation(continuation) ?: continuation
            intercepted.resumeWith(result)
        }

        fun getOrSuspend(): Any? {
            if (compareAndSet(UNDECIDED, SUSPENDED)) {
                return COROUTINE_SUSPENDED
            }
            @Suppress("UNCHECKED_CAST")
            return (get() as Result<Any?>).getOrThrow()
        }
    }

    private val UNDECIDED = Any()
    private val SUSPENDED = Any()
}
