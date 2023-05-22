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
    void testJobCondition(ExampleJob exampleJob) throws InterruptedException {
        assertTrue(exampleJob.isPaused());
        assertFalse(exampleJob.hasJobRun());
        Thread.sleep(5000);
        assertFalse(exampleJob.hasJobRun());
        exampleJob.unpause();
        await().atMost(3, SECONDS).until(exampleJob::hasJobRun);
        assertTrue(exampleJob.hasJobRun());
    }
}
