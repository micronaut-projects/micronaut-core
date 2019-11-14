package io.micronaut.runtime.kotlin.coroutines.intercept

import io.micronaut.aop.Around
import io.micronaut.context.annotation.Type
import io.micronaut.core.annotation.Internal


/**
 * Internal AOP around advice to support Kotlin coroutines at the framework level. Typically applied at
 * compilation time.
 *
 * @author graemerocher
 * @since 1.3.0
 * @see KotlinCoroutineAroundInterceptor
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
@Around
@Type(KotlinCoroutineAroundInterceptor::class)
@Internal
internal annotation class KotlinCoroutineAroundAdvice(
        /**
         * @return Is the coroutine type a Kotlin Unit.
         */
        val unit: Boolean = false)
