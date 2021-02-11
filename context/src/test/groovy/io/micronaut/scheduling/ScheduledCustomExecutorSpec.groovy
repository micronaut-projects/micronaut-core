package io.micronaut.scheduling

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.annotation.Scheduled
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class ScheduledCustomExecutorSpec extends Specification {

    void "test scheduled with a custom executor"() {
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name': 'ScheduledCustomExecutorSpec',
                'micronaut.executors.dispatcher.type': 'scheduled',
                'micronaut.executors.dispatcher.core-pool-size': 1
        ])

        when:
        ScheduledBean bean = ctx.getBean(ScheduledBean)
        PollingConditions conditions = new PollingConditions(timeout: 1)

        then:
        conditions.eventually {
            bean.ran
        }
    }

    @Requires(property = "spec.name", value = "ScheduledCustomExecutorSpec")
    @javax.inject.Singleton
    static class ScheduledBean {

        public boolean ran = false

        @Scheduled(initialDelay = "10ms", scheduler = "dispatcher")
        void run() {
            ran = true
        }
    }

}
