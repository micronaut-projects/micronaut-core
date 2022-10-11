package io.micronaut.docs.server.suspend

import io.micronaut.http.HttpRequest
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.retry.annotation.Retryable
import kotlinx.coroutines.delay
import jakarta.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
open class SuspendService(
    private val suspendRequestScopedService: SuspendRequestScopedService
) {
    var counter1: Int = 0
    var counter2: Int = 0
    var counter3: Int = 0
    var counter4: Int = 0

    @Retryable
    open suspend fun delayedCalculation1(): String {
        if (counter1 != 2) {
            delay(1)
            counter1++
            throw RuntimeException("error $counter1")
        }
        delay(1)
        return "delayedCalculation1"
    }

    @Retryable
    open suspend fun delayedCalculation2(): String {
        if (counter2 != 2) {
            delay(1)
            counter2++
            throw RuntimeException("error $counter2")
        }
        delay(1)
        return "delayedCalculation2"
    }

    @Retryable
    open suspend fun calculation3(): String {
        if (counter3 != 2) {
            counter3++
            throw RuntimeException("error $counter3")
        }
        return "delayedCalculation3"
    }

    @Retryable
    open suspend fun requestScopedCalculation(): String {
        if (counter4 != 2) {
            counter4++
            throw RuntimeException("error $counter4")
        }
        return "${suspendRequestScopedService.requestId},${Thread.currentThread().id}"
    }

    suspend fun requestContext(): String {
        delay(1)
        // called from a suspend controller function
        val currentRequest = ServerRequestContext.currentRequest<HttpRequest<Any>>().orElseGet {
            error("Expected a current http server request")
        }
        return currentRequest.path
    }

    suspend fun findMyContextValue(): String? {
        return coroutineContext[MyContext]?.value
    }

    @MyContextInterceptorAnn
    open suspend fun call1(): String? {
        return findMyContextValue()
    }

    @MyContextInterceptorAnn
    open suspend fun call2(): String? {
        return call1()
    }

    open suspend fun call3(): String? {
        return call1()
    }

    @MyContextInterceptorAnn
    open suspend fun call4(): String? {
        return call3()
    }
}
