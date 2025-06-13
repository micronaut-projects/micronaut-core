/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.context.propagation.instrument.execution

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.core.order.Ordered
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.scheduling.instrument.InstrumentedExecutorService
import io.micronaut.scheduling.instrument.InstrumentedScheduledExecutorService
import spock.lang.Issue
import spock.lang.Specification

import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService

class ExecutorServiceInstrumenterSpec extends Specification {
    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/11653")
    void "test ExecutorServiceInstrumenter instruments executor service if other instrumentations are present"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run([
                'spec.name': 'ExecutorServiceInstrumenterSpec'
        ])

        when:
        ExecutorService io = applicationContext.getBean(ExecutorService, Qualifiers.byName("io"))

        then:"The last instrumentation is applied"
        io instanceof InstrumentedExecutorService

        and:"The context propagation instrumentation is applied"
        (io as InstrumentedExecutorService).getTarget() instanceof ContextPropagatingExecutorService

        and:"The first instrumentation is applied"
        ((io as InstrumentedExecutorService).getTarget() as InstrumentedExecutorService).getTarget() instanceof InstrumentedExecutorService

        cleanup:
        applicationContext.close()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/11653")
    void "test ExecutorServiceInstrumenter instruments scheduled executor service if other instrumentations are present"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run([
                'spec.name': 'ExecutorServiceInstrumenterSpec'
        ])

        when:
        ExecutorService scheduled = applicationContext.getBean(ExecutorService, Qualifiers.byName("scheduled"))

        then:"The last instrumentation is applied"
        scheduled instanceof InstrumentedScheduledExecutorService

        and:"The context propagation instrumentation is applied"
        (scheduled as InstrumentedExecutorService).getTarget() instanceof ContextPropagatingScheduledExecutorService

        and:"The first instrumentation is applied"
        ((scheduled as InstrumentedExecutorService).getTarget() as InstrumentedExecutorService).getTarget() instanceof InstrumentedScheduledExecutorService

        cleanup:
        applicationContext.close()
    }

    abstract static class ExecutorServiceInstrumentation implements BeanCreatedEventListener<ExecutorService>, Ordered {
        @Override
        ExecutorService onCreated(BeanCreatedEvent<ExecutorService> event) {
            ExecutorService executorService = event.bean
            if (executorService instanceof ScheduledExecutorService) {
                return new InstrumentedScheduledExecutorService() {
                    @Override
                    ScheduledExecutorService getTarget() {
                        return executorService as ScheduledExecutorService
                    }

                    @Override
                    void execute(Runnable command) {
                        getTarget().execute(instrument(command))
                    }
                }
            } else {
                return new InstrumentedExecutorService() {
                    @Override
                    ExecutorService getTarget() {
                        return executorService
                    }

                    @Override
                    void execute(Runnable command) {
                        getTarget().execute(instrument(command))
                    }
                }
            }
        }
    }

    @Requires(property = 'spec.name', value = 'ExecutorServiceInstrumenterSpec')
    @Prototype
    static class FirstExecutorServiceInstrumentation extends ExecutorServiceInstrumentation {
        @Override
        int getOrder() {
            // Ensure this instrumentation is applied before the ExecutorServiceInstrumenter
            return HIGHEST_PRECEDENCE
        }
    }

    @Requires(property = 'spec.name', value = 'ExecutorServiceInstrumenterSpec')
    @Prototype
    static class LastExecutorServiceInstrumentation extends ExecutorServiceInstrumentation {
        @Override
        int getOrder() {
            // Ensure this instrumentation is applied after the ExecutorServiceInstrumenter
            return LOWEST_PRECEDENCE
        }
    }
}
