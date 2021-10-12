package io.micronaut.scheduling

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.retry.annotation.Retryable
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.atomic.AtomicInteger

class RetrySchedulingSpec extends Specification {
    void 'test scheduled annotation with retry'() {
        given:
        ApplicationContext beanContext = ApplicationContext.run(
                'scheduled-test.task2.enabled':true
        )

        PollingConditions conditions = new PollingConditions(timeout: 10)

        when:
        MyTask2 myTask = beanContext.getBean(MyTask2)

        then:
        conditions.eventually {
            myTask.initialDelayWasRun
            myTask.attempts.get() == 2
        }
    }

    @Singleton
    @Requires(property = 'scheduled-test.task2.enabled', value = 'true')
    static class MyTask2 {

        boolean initialDelayWasRun = false
        AtomicInteger attempts = new AtomicInteger()

        @Retryable(delay = "10ms")
        @Scheduled(initialDelay = '10ms')
        void runInitialDelay() {
            if (!Thread.currentThread().getName().startsWith("scheduled-executor-thread")) {
                throw new RuntimeException("Incorrect thread name")
            }
            if (attempts.addAndGet(1) < 2) {
                throw new RuntimeException()
            }
            initialDelayWasRun = true
        }
    }
}
