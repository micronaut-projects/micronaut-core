package io.micronaut.docs.aop.lifecycle.pertarget

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.context.scope.refresh.RefreshEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AuditInterceptorSpec {

    @Test
    fun testEachTargetHasItsOwnInterceptor() {
        ApplicationContext.run().use { context ->
            // tag::test[]
            val greeter = context.getBean(Greeter::class.java)

            assertEquals("Hello Fred (call 1)", greeter.greet("Fred")) // <1>
            assertEquals("Goodbye Fred (call 2)", greeter.farewell("Fred"))

            context.publishEvent(RefreshEvent()) // <2>

            assertEquals("Hello Bob (call 1)", greeter.greet("Bob")) // <3>
            assertEquals("Goodbye Bob (call 2)", greeter.farewell("Bob"))
            // end::test[]
        }
    }
}
