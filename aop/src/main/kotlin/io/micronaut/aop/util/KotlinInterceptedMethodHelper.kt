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
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * A helper utility class for intercepting Kotlin suspend methods as a value of [CompletionStage].
 *
 * @author Benedikt Hunger
 * @since 3.5.0
 */
@Internal
@Experimental
internal object KotlinInterceptedMethodHelper {
    @JvmStatic
    suspend fun handleResult(result: CompletionStage<*>, isUnitValueType: Boolean): Any? = suspendCoroutine { continuation ->
        result.whenComplete { value: Any?, throwable: Throwable? ->
            if (throwable == null) {
                val res = Result.success(value ?: if (isUnitValueType) Unit else null)
                continuation.resumeWith(res)
            } else {
                val exception = if (throwable is CompletionException) { throwable.cause ?: throwable } else throwable
                continuation.resumeWithException(exception)
            }
        }
    }
}
