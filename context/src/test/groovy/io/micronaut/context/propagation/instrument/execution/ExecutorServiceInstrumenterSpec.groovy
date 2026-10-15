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
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.core.order.Ordered
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.scheduling.instrument.InstrumentedExecutorService
import io.micronaut.scheduling.instrument.InstrumentedScheduledExecutorService
import jakarta.inject.Named
import jakarta.inject.Singleton
import spock.lang.Issue
import spock.lang.Specification

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class ExecutorServiceInstrumenterSpec extends Specification {
    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/11653")
    void "test the context propagation of an ExecutorFactory executor is kept when other instrumentations are present"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run([
                'spec.name': 'ExecutorServiceInstrumenterSpec'
        ])

        when:
        ExecutorService io = applicationContext.getBean(ExecutorService, Qualifiers.byName("io"))
        ExecutorService first = (io as InstrumentedExecutorService).getTarget()

        then:"The last instrumentation is applied"
        io instanceof InstrumentedExecutorService
        !(io instanceof ContextPropagatingExecutorService)

        and:"The first instrumentation is applied"
        first instanceof InstrumentedExecutorService
        !(first instanceof ContextPropagatingExecutorService)

        and:"The context propagation instrumentation is applied by the factory, innermost, so the other instrumentations run with the context on the worker"
        (first as InstrumentedExecutorService).getTarget() instanceof ContextPropagatingExecutorService
        ContextPropagatingExecutorService.isInstrumented(io)

        cleanup:
        applicationContext.close()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/11653")
    void "test the context propagation of an ExecutorFactory scheduled executor is kept when other instrumentations are present"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run([
                'spec.name': 'ExecutorServiceInstrumenterSpec'
        ])

        when:
        ExecutorService scheduled = applicationContext.getBean(ExecutorService, Qualifiers.byName("scheduled"))
        ExecutorService first = (scheduled as InstrumentedExecutorService).getTarget()

        then:"The last instrumentation is applied"
        scheduled instanceof InstrumentedScheduledExecutorService
        !(scheduled instanceof ContextPropagatingExecutorService)

        and:"The first instrumentation is applied"
        first instanceof InstrumentedScheduledExecutorService
        !(first instanceof ContextPropagatingExecutorService)

        and:"The context propagation instrumentation is applied by the factory, innermost"
        (first as InstrumentedExecutorService).getTarget() instanceof ContextPropagatingScheduledExecutorService

        cleanup:
        applicationContext.close()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/11653")
    void "test ExecutorServiceInstrumenter instruments an application executor service if other instrumentations are present"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run([
                'spec.name': 'ExecutorServiceInstrumenterSpec'
        ])

        when:
        ExecutorService custom = applicationContext.getBean(ExecutorService, Qualifiers.byName("custom"))

        then:"The last instrumentation is applied"
        custom instanceof InstrumentedExecutorService

        and:"The context propagation instrumentation is applied"
        (custom as InstrumentedExecutorService).getTarget() instanceof ContextPropagatingExecutorService

        and:"The first instrumentation is applied"
        ((custom as InstrumentedExecutorService).getTarget() as InstrumentedExecutorService).getTarget() instanceof InstrumentedExecutorService

        cleanup:
        applicationContext.close()
    }

    @Requires(property = 'spec.name', value = 'ExecutorServiceInstrumenterSpec')
    @Factory
    static class CustomExecutorFactory {
        @Singleton
        @Named("custom")
        @Bean(preDestroy = "shutdown")
        ExecutorService custom() {
            return Executors.newSingleThreadExecutor()
        }
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
