package io.micronaut.scheduling

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class ScheduledInterceptedSpec extends Specification {

    void "test scheduled with an intercepted bean"() {
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name': 'ScheduledInterceptedSpec'
        ])

        when:
        ScheduledInterceptedSpecTask bean = ctx.getBean(ScheduledInterceptedSpecTask)
        Thread.sleep(1000)

        then:
        bean.localCounter.get() <= 15
    }

}
