package io.micronaut.scheduling.beanproperties

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class ScheduledBeanPropertiesSpec extends Specification {

    void 'test schedule task at fixed delay or rate '() {
        given:
        ApplicationContext beanContext = ApplicationContext.run(
                'scheduled-test.task.enabled':true,
                'spec.name': 'ScheduledBeanPropertiesSpec',
                'scheduling.string-fixed-rate': '5s',
                'scheduling.duration-fixed-rate': '6s'
        )

        PollingConditions conditions = new PollingConditions(timeout: 7)

        when:
        PropertiesTask propertiesTask = beanContext.getBean(PropertiesTask)
        JavaPropertiesTask javaPropertiesTask = beanContext.getBean(JavaPropertiesTask)

        then:
        !propertiesTask.wasDelayedRun
        conditions.eventually {
            propertiesTask.wasRun
            propertiesTask.fixedDelayWasRun
            javaPropertiesTask.fixedRateEvents.get() == 2
        }

        and:
        conditions.eventually {
            propertiesTask.wasRun
            propertiesTask.cronEvents.get() >= 2
            propertiesTask.cronEventsNoSeconds.get() >= 0
            propertiesTask.wasDelayedRun
        }

        cleanup:
        beanContext.close()
    }

    @Singleton
    static class SchedulingConfig {

        String cron = '1/3 0/1 * 1/1 * ?';
        String cronNoSeconds = '0/1 * 1/1 * ?';
        Duration fixedRate = Duration.ofMillis(10);
        Duration fixedDelay = Duration.ofMillis(10);
        Duration initialDelay = Duration.ofSeconds(1)

    }

    @Singleton
    @Requires(property = 'spec.name', value = 'ScheduledBeanPropertiesSpec')
    @Requires(property = 'scheduled-test.task.enabled', value = 'true')
    static class PropertiesTask {

        boolean wasRun = false
        boolean wasDelayedRun = false
        boolean fixedDelayWasRun = false
        AtomicInteger cronEvents = new AtomicInteger(0)
        AtomicInteger cronEventsNoSeconds = new AtomicInteger(0)

        @Scheduled(bean = SchedulingConfig.class, fixedRateProperty = 'fixedRate')
        void runSomething() {
            wasRun = true
        }

        @Scheduled(bean = SchedulingConfig.class, cronProperty = 'cron')
        void runCron() {
            cronEvents.incrementAndGet()
        }

        @Scheduled(bean = SchedulingConfig.class, cronProperty = 'cronNoSeconds')
        void runCronNoSeconds() {
            cronEventsNoSeconds.incrementAndGet()
        }

        @Scheduled(bean = SchedulingConfig.class, fixedDelayProperty = 'fixedDelay')
        void runFixedDelay() {
            fixedDelayWasRun = true
        }

        @Scheduled(bean = SchedulingConfig.class, fixedRateProperty = 'fixedRate', initialDelayProperty = 'initialDelay')
        void runSomethingElse() {
            wasDelayedRun = true
        }
    }
}
