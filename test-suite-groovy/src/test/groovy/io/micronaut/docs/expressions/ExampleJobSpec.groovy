package io.micronaut.docs.expressions

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared;
import spock.lang.Specification

import static java.util.concurrent.TimeUnit.SECONDS
import static org.awaitility.Awaitility.await

class ExampleJobSpec extends Specification {
    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run()

    void testJobCondition(){
        given:
        ExampleJob exampleJob = ctx.getBean(ExampleJob)
        ExampleJobControl jobControl = ctx.getBean(ExampleJobControl)

        expect:
        jobControl.isPaused()
        !exampleJob.hasJobRun()

        when:
        Thread.sleep(5000)

        then:
        !exampleJob.hasJobRun()

        when:
        jobControl.unpause()

        then:
        await().atMost(3, SECONDS).until(exampleJob::hasJobRun)

        and:
        exampleJob.hasJobRun()
    }
}
