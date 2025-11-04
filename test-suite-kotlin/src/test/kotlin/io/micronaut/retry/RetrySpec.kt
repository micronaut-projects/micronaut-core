package io.micronaut.retry

import io.micronaut.context.ApplicationContext
import io.micronaut.retry.annotation.Retryable
import io.micronaut.retry.event.RetryEvent
import io.micronaut.retry.event.RetryEventListener
import jakarta.inject.Singleton
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RetrySpec {

    @Test
    fun testRetryWithIncludes() {
        val context = ApplicationContext.run()
        val counterService = context.getBean(CounterService::class.java)

        assertFailsWith(IllegalStateException::class) {
            counterService.getCountIncludes(true)
        }
        assertEquals(counterService.countIncludes, 1)

        counterService.getCountIncludes(false)

        assertEquals(counterService.countIncludes, counterService.countThreshold)

        context.stop()
    }

    @Test
    fun testRetryWithExcludes() {
        val context = ApplicationContext.run()
        val counterService = context.getBean(CounterService::class.java)

        assertFailsWith(MyCustomException::class) {
            counterService.getCountExcludes(false)
        }

        assertEquals(counterService.countExcludes, 1)

        counterService.getCountExcludes(true)

        assertEquals(counterService.countExcludes, counterService.countThreshold)

        context.stop()
    }

    @Singleton
    class MyRetryListener : RetryEventListener {

        val events: ArrayList<RetryEvent> = ArrayList()

        fun reset() {
            events.clear()
        }

        override fun onApplicationEvent(event: RetryEvent) {
            events.add(event)
        }
    }

    @Singleton
    open class CounterService {

        var countIncludes = 0
        var countExcludes = 0
        var countThreshold = 3

        @Retryable(attempts = "5", delay = "5ms", includes = [MyCustomException::class])
        open fun getCountIncludes(illegalState: Boolean): Int  {
            countIncludes++
            if(countIncludes < countThreshold) {
                if (illegalState) {
                    throw IllegalStateException("Bad count")
                } else {
                    throw MyCustomException()
                }
            }
            return countIncludes
        }

        @Retryable(attempts = "5", delay = "5ms", excludes = [MyCustomException::class])
        open fun getCountExcludes(illegalState: Boolean): Int {
            countExcludes++
            if(countExcludes < countThreshold) {
                if (illegalState) {
                    throw IllegalStateException("Bad count")
                } else {
                    throw MyCustomException()
                }
            }
            return countExcludes
        }
    }
}
