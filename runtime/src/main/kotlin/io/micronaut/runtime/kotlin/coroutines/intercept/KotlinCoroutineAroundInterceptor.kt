package io.micronaut.runtime.kotlin.coroutines.intercept

import io.micronaut.aop.InterceptPhase
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.runtime.kotlin.support.KotlinUtils
import io.reactivex.Completable
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

import javax.inject.Singleton
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

/**
 * Internal AOP around advice to support Kotlin coroutines at the framework level. Typically applied at
 * compilation time.
 *
 * @author graemerocher
 * @since 1.3.0
 * @see KotlinCoroutineAroundAdvice
 */
@Singleton
@Requires(classes = [Continuation::class])
class KotlinCoroutineAroundInterceptor : MethodInterceptor<Any, Any> {

    override fun getOrder(): Int {
        return InterceptPhase.CACHE.position + 10
    }

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any {
        val isUnit = context.isTrue(KotlinCoroutineAroundAdvice::class.java, "unit")
        val parameterValues = context.parameterValues
        val continuation = CustomContinuation()
        parameterValues[parameterValues.size - 1] = continuation
        val result = context.proceed()
        return if (KotlinUtils.isKotlinCoroutineSuspended(result)) {
            Publishers.fromCompletableFuture(continuation)
        } else {
            if (isUnit) {
                Completable.complete().toFlowable<Any>()
            } else {
                Publishers.just(result)
            }
        }
    }
}

private class CustomContinuation: Continuation<Any>, Supplier<CompletableFuture<Any>> {
    private val completableFuture = CompletableFuture<Any>()

    override fun get(): CompletableFuture<Any> = completableFuture

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
