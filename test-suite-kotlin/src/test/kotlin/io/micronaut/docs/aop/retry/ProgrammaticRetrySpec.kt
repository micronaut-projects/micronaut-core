package io.micronaut.docs.aop.retry

import io.micronaut.context.ApplicationContext
import io.micronaut.retry.exception.CircuitOpenException
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProgrammaticRetrySpec {

    @Test
    fun testProgrammaticRetryExamples() {
        val context = ApplicationContext.run()
        val service = context.getBean(ProgrammaticBookService::class.java)

        service.reset()
        assertEquals("The Stand", service.listBooks().first().title)

        service.reset()
        assertEquals("The Stand", Mono.from(service.streamBooks()).block()!!.title)

        service.reset()
        assertEquals("The Stand", service.findBook("The Stand").toCompletableFuture().get().title)

        service.reset()
        assertEquals("The Stand", service.findBookWithCircuitBreaker("The Stand").title)

        context.close()
    }
}
