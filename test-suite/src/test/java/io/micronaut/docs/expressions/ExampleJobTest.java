package io.micronaut.docs.expressions;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
public class ExampleJobTest {

    @Test
    void testJobCondition(ExampleJob exampleJob, ExampleJobControl jobControl) throws InterruptedException {
        assertTrue(jobControl.isPaused());
        assertFalse(exampleJob.hasJobRun());
        Thread.sleep(5000);
        assertFalse(exampleJob.hasJobRun());
        jobControl.unpause();
        await().atMost(3, SECONDS).until(exampleJob::hasJobRun);
        assertTrue(exampleJob.hasJobRun());
    }
}
