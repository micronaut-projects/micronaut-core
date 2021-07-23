package io.micronaut.scheduling

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.annotation.Scheduled
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.atomic.AtomicInteger

class ScheduledCustomExecutorSpec extends Specification {

    void "test scheduled with a custom executor"() {
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name': 'ScheduledCustomExecutorSpec',
                'micronaut.executors.dispatcher.type': 'scheduled',
                'micronaut.executors.dispatcher.core-pool-size': 1
        ])

        when:
        ScheduledBean bean = ctx.getBean(ScheduledBean)
        PollingConditions conditions = new PollingConditions(timeout: 10)

        then:
        conditions.eventually {
            bean.ran
            bean.cronEvents.get() >= 2
        }
    }

    @Requires(property = "spec.name", value = "ScheduledCustomExecutorSpec")
    @jakarta.inject.Singleton
    static class ScheduledBean {

        public boolean ran = false
        AtomicInteger cronEvents = new AtomicInteger(0)

        @Scheduled(initialDelay = "10ms", scheduler = "dispatcher")
        void run() {
            ran = true
        }

        @Scheduled(cron = '1/3 0/1 * 1/1 * ?', scheduler = "dispatcher")
        @Scheduled(cron = '1/4 0/1 * 1/1 * ?', scheduler = "dispatcher")
        void runCron() {
            cronEvents.incrementAndGet()
        }
    }

}
