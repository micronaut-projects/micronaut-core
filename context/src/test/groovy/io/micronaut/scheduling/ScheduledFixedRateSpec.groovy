/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.scheduling

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.annotation.Scheduled
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import jakarta.inject.Singleton
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author graemerocher
 * @since 1.0
 */
class ScheduledFixedRateSpec extends Specification {

    void 'test schedule task at fixed delay or rate '() {
        given:
        ApplicationContext beanContext = ApplicationContext.run(
                'some.configuration':'10ms',
                'scheduled-test.task.enabled':true,
                'spec.name': 'ScheduledFixedRateSpecMyTask',
        )

        PollingConditions conditions = new PollingConditions(timeout: 7)

        when:
        MyTask myTask = beanContext.getBean(MyTask)
        MyJavaTask myJavaTask = beanContext.getBean(MyJavaTask)

        then:
        !myTask.wasDelayedRun
        conditions.eventually {
            myTask.wasRun
            myTask.fixedDelayWasRun
            myJavaTask.wasRun
            myJavaTask.wasRun
            myJavaTask.cronEvents.get() == 2
        }

        and:
        conditions.eventually {
            myTask.wasRun
            myTask.cronEvents.get() >= 2
            myTask.cronEventsNoSeconds.get() >= 0
            myTask.wasDelayedRun
        }

        cleanup:
        beanContext.close()
    }

    void 'test multiple schedule annotations'() {
        given:
        ApplicationContext beanContext = ApplicationContext.run(
                'scheduled-test.task.enabled': true,
                'spec.name': 'ScheduledFixedRateSpecMyOtherTask',
        )

        PollingConditions conditions = new PollingConditions(timeout: 20)

        when:
        MyOtherTask myTask = beanContext.getBean(MyOtherTask)

        then:
        conditions.eventually {
            myTask.cronEvents.get() == 2
        }

        cleanup:
        beanContext.close()
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'ScheduledFixedRateSpecMyTask')
    @Requires(property = 'scheduled-test.task.enabled', value = 'true')
    static class MyTask {

        boolean wasRun = false
        boolean wasDelayedRun = false
        boolean fixedDelayWasRun = false
        boolean configuredWasRun = false
        AtomicInteger cronEvents = new AtomicInteger(0)
        AtomicInteger cronEventsNoSeconds = new AtomicInteger(0)

        @Scheduled(fixedRate = '10ms')
        void runSomething() {
            wasRun = true
        }

        @Scheduled(cron = '1/3 0/1 * 1/1 * ?')
        void runCron() {
            cronEvents.incrementAndGet()
        }

        @Scheduled(cron = '0/1 * 1/1 * ?')
        void runCronNoSeconds() {
            cronEventsNoSeconds.incrementAndGet()
        }

        @Scheduled(fixedRate = '${some.configuration}')
        void runScheduleConfigured() {
            configuredWasRun = true
        }

        @Scheduled(fixedDelay = '10ms')
        void runFixedDelay() {
            fixedDelayWasRun = true
        }

        @Scheduled(fixedRate = '10ms', initialDelay = '1s')
        void runSomethingElse() {
            wasDelayedRun = true
        }
    }

    @Singleton
    @Requires(property = 'scheduled-test.task.enabled', value = 'true')
    static class MyOtherTask {

        AtomicInteger cronEvents = new AtomicInteger(0)

        @Scheduled(cron = '1/3 0/1 * 1/1 * ?')
        @Scheduled(cron = '1/4 0/1 * 1/1 * ?')
        void runCron() {
            cronEvents.incrementAndGet()
        }
    }
}
