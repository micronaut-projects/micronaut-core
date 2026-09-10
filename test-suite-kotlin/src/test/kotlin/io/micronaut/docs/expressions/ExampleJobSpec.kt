package io.micronaut.docs.expressions

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExampleJobSpec {

    @Test
    fun testJobCondition() {
        ApplicationContext.run(mapOf("spec.name" to "ExampleJobTest")).use { ctx ->
            val exampleJob = ctx.getBean(ExampleJob::class.java)

            assertTrue(exampleJob.paused)
            assertFalse(exampleJob.hasJobRun())

            Thread.sleep(5000)
            assertFalse(exampleJob.hasJobRun())

            exampleJob.unpause()

            val deadline = System.currentTimeMillis() + 3000
            while (!exampleJob.hasJobRun() && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
            }
            assertTrue(exampleJob.hasJobRun())
        }
    }
}
