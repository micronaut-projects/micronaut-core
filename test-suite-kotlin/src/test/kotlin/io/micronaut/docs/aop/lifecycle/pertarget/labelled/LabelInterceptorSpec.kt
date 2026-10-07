package io.micronaut.docs.aop.lifecycle.pertarget.labelled

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LabelInterceptorSpec {

    @Test
    fun testEachTargetIsLabelledWithItsQualifier() {
        ApplicationContext.run(mapOf(
            "labelled-channels.sms.sender" to "Fred",
            "labelled-channels.email.sender" to "Bob"
        )).use { context ->
            // tag::test[]
            val sms = context.getBean(Channel::class.java, Qualifiers.byName("sms"))
            val email = context.getBean(Channel::class.java, Qualifiers.byName("email"))

            assertEquals("[sms] Hello from Fred", sms.send("Hello"))
            assertEquals("[email] Hello from Bob", email.send("Hello"))
            // end::test[]
        }
    }
}
