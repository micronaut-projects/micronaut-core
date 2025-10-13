package io.micronaut.reproduce

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ReproduceIssueSpec {

    @Test
    fun `Kotlin child inherits field from Java parent with KSP`() {
        val ctx = ApplicationContext.run()

        // Correctly reference the class for Micronaut to create a bean
        assertThrows<IllegalStateException> {
            ctx.getBean(Child::class.java)
        }
    }
}


