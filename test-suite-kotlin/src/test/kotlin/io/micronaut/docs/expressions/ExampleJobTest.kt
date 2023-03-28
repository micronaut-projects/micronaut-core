package io.micronaut.docs.expressions

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

@MicronautTest
class ExampleJobTest {
    @Test
    fun testJobCondition(exampleJob: ExampleJob, exampleJobControl: ExampleJobControl) {
        Assertions.assertTrue(exampleJobControl.paused)
        Assertions.assertFalse(exampleJob.hasJobRun())
        Thread.sleep(5000)
        Assertions.assertFalse(exampleJob.hasJobRun())
        exampleJobControl.unpause()
        org.awaitility.Awaitility.await().atMost(3, TimeUnit.SECONDS).until(Callable { exampleJob.hasJobRun() })
        Assertions.assertTrue(exampleJob.hasJobRun())
    }
}
