package io.micronaut.annotation.processing

import io.kotest.matchers.shouldBe
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertTrue

@MicronautTest
class SuspendFunctionInterceptorSpec {

    @Inject
    lateinit var demoClient: DemoClient

    @Test
    fun interceptSuspendMethod() {
        val interceptor = TestCoroutineInterceptor()
        val latch = CountDownLatch(1)
        var answer: String? = null
        demoClient::getSyncString.startCoroutine(Continuation(interceptor) { result ->
            answer = result.getOrNull()
            latch.countDown()
        })
        latch.await(1, TimeUnit.SECONDS) shouldBe true
        assertTrue(interceptor.didIntercept())
        answer shouldBe "sync string"
    }

    @Test
    fun returnToCallerThreadWithSuspendClient() {
        val singleThreadDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        runBlocking {
            launch(singleThreadDispatcher) {
                val threadId = Thread.currentThread().id
                demoClient.getSyncString() shouldBe "sync string"
                Thread.currentThread().id shouldBe threadId
            }
        }
    }

    @Client("/demo")
    @Consumes(MediaType.TEXT_PLAIN)
    interface DemoClient {
        @Get("/sync/string")
        suspend fun getSyncString(): String
    }

    class TestCoroutineInterceptor : ContinuationInterceptor {
        private val didIntercept = AtomicBoolean(false)

        fun didIntercept() = didIntercept.get()

        override val key: CoroutineContext.Key<*>
            get() = ContinuationInterceptor.Key

        override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
            return InterceptedContinuation(didIntercept, continuation)
        }

        class InterceptedContinuation<T>(
            private val didIntercept: AtomicBoolean,
            private val continuation: Continuation<T>
        ) : Continuation<T> {
            override val context: CoroutineContext
                get() = continuation.context

            override fun resumeWith(result: Result<T>) {
                if (result as Any? !== Unit) { // startCoroutine directly calls resumeWith(Unit) after starting the coroutine
                    didIntercept.set(true)
                }
                continuation.resumeWith(result)
            }
        }
    }
}
