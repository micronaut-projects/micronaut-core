package io.micronaut.scheduling.exceptions

import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class ScheduledExceptionHandlingSpec extends Specification {

    void "test that a task that throws a specific exception is handled by the correct handler"() {
        given:
        ApplicationContext ctx = ApplicationContext.run('scheduled-exception1.task.enabled':'true')
        PollingConditions conditions = new PollingConditions(timeout: 10, delay: 0.5)

        expect:
        conditions.eventually {
            ctx.getBean(BeanAndTypeSpecificHandler).getThrowable()
            ctx.getBean(BeanAndTypeSpecificHandler).getBean()
            !ctx.getBean(TypeSpecificHandler).getThrowable()
            !ctx.getBean(TypeSpecificHandler).getBean()
        }

        cleanup:
        ctx.close()
    }
}
