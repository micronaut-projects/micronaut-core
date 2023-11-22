package io.micronaut.docs.expressions

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static java.util.concurrent.TimeUnit.SECONDS
import static org.awaitility.Awaitility.await

class ExampleJobSpec extends Specification {
    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(["spec.name": "ExampleJobTest"])

    void testJobCondition() {
        given:
        ExampleJob exampleJob = ctx.getBean(ExampleJob)

        expect:
        exampleJob.isPaused()
        !exampleJob.hasJobRun()

        when:
        Thread.sleep(5000)

        then:
        !exampleJob.hasJobRun()

        when:
        exampleJob.unpause()

        then:
        await().atMost(3, SECONDS).until(exampleJob::hasJobRun)

        and:
        exampleJob.hasJobRun()
    }
}
