package io.micronaut.docs.expressions

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.awaitility.Awaitility
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

@MicronautTest
class ExampleJobTest {
    @Test
    fun testJobCondition(exampleJob: ExampleJob) {
        Assertions.assertTrue(exampleJob.paused)
        Assertions.assertFalse(exampleJob.hasJobRun())
        Thread.sleep(5000)
        Assertions.assertFalse(exampleJob.hasJobRun())
        exampleJob.unpause()
        Awaitility.await().atMost(3, TimeUnit.SECONDS).until { exampleJob.hasJobRun() }
        Assertions.assertTrue(exampleJob.hasJobRun())
    }
}
