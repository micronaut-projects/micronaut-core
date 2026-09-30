/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CopyOnWriteArrayList

class ScheduledExecutionSpec extends Specification {

    void 'each schedule of a method is the current execution while it triggers the method'() {
        given:
        ApplicationContext context = ApplicationContext.run('spec.name': 'ScheduledExecutionSpec')
        TwiceScheduled task = context.getBean(TwiceScheduled)

        expect:
        new PollingConditions(timeout: 10).eventually {
            task.initialDelays.toSet() == ['10ms', '50ms'] as Set
        }
        task.methods.toSet() == ['run'] as Set

        cleanup:
        context.close()
    }

    void 'a call the application makes has no current execution'() {
        given:
        ApplicationContext context = ApplicationContext.run('spec.name': 'ScheduledExecutionSpec')
        TwiceScheduled task = context.getBean(TwiceScheduled)

        expect:
        !ScheduledExecution.current().isPresent()
        task.direct() == 'none'

        cleanup:
        context.close()
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'ScheduledExecutionSpec')
    static class TwiceScheduled {

        final List<String> initialDelays = new CopyOnWriteArrayList<>()
        final List<String> methods = new CopyOnWriteArrayList<>()

        @Scheduled(fixedDelay = '1h', initialDelay = '10ms')
        @Scheduled(fixedDelay = '2h', initialDelay = '50ms')
        void run() {
            ScheduledExecution execution = ScheduledExecution.current().orElseThrow()
            initialDelays.add(execution.schedule().stringValue('initialDelay').orElse(''))
            methods.add(execution.method().methodName)
        }

        String direct() {
            ScheduledExecution.current().map { it.method().methodName }.orElse('none')
        }
    }
}
