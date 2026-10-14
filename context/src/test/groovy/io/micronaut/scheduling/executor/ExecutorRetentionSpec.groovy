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
package io.micronaut.scheduling.executor

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanRegistration
import io.micronaut.context.DefaultBeanContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * An executor service holds nothing bound to its context, so a bean that received one can be retained across a
 * restart in development mode, with the executor service.
 */
class ExecutorRetentionSpec extends Specification {

    void "a bean that received an executor service is retained with it, and the adopting context serves the same executor service"() {
        given:
        ApplicationContext first = start(List.of())
        ExecutorHolder holder = first.getBean(ExecutorHolder)
        ExecutorService executor = holder.executor

        when:
        Collection<BeanRegistration<?>> retained = ((DefaultBeanContext) first).stopRetaining { ExecutorHolder.isAssignableFrom(it.beanType) }

        then: "the holder is retained with the executor service it received, which is not shut down"
        retained*.bean.any { it.is(holder) }
        retained*.bean.any { it.is(executor) }
        !executor.shutdown

        when:
        ApplicationContext second = start(retained)

        then: "the new context serves both, and the executor service still runs tasks on the threads of its configuration"
        second.getBean(ExecutorHolder).is(holder)
        second.getBean(ExecutorService, Qualifiers.byName("retained")).is(executor)
        executor.submit({ Thread.currentThread().name } as Callable<String>).get(10, TimeUnit.SECONDS).startsWith("retained-executor")

        when:
        second.close()

        then: "it is shut down with the context that adopted it"
        executor.shutdown
    }

    void "an executor service is made with the thread factory of its configuration's name"() {
        given:
        ApplicationContext context = start(List.of())

        expect:
        context.getBean(ExecutorService, Qualifiers.byName("retained")).submit({ Thread.currentThread().name } as Callable<String>)
            .get(10, TimeUnit.SECONDS).startsWith("retained-executor")
        context.getBean(ThreadFactory, Qualifiers.byName("retained")) instanceof NamedThreadFactory

        cleanup:
        context.close()
    }

    void "a retained scheduled executor service keeps running through the graceful shutdown of the stopping context"() {
        given:
        Map<String, Object> scheduled = ['micronaut.executors.retained.type': 'scheduled', 'micronaut.executors.retained.core-pool-size': '1',
                                         'micronaut.lifecycle.graceful-shutdown.enabled': 'true']
        ApplicationContext first = start(List.of(), scheduled)
        ExecutorService executor = first.getBean(ExecutorHolder).executor

        when:
        Collection<BeanRegistration<?>> retained = ((DefaultBeanContext) first).stopRetaining { ExecutorHolder.isAssignableFrom(it.beanType) }

        then: "the graceful shutdown left it running"
        retained*.bean.any { it.is(executor) }
        !executor.shutdown

        when:
        ApplicationContext second = start(retained, scheduled)

        then:
        second.getBean(ExecutorService, Qualifiers.byName("retained")).is(executor)
        executor.submit({ Thread.currentThread().name } as Callable<String>).get(10, TimeUnit.SECONDS).startsWith("retained-executor")

        when: "a stop that retains nothing shuts it down gracefully"
        second.close()

        then:
        executor.shutdown
    }

    private static ApplicationContext start(Collection<BeanRegistration<?>> retained, Map<String, Object> properties = ['micronaut.executors.retained.type': 'fixed', 'micronaut.executors.retained.number-of-threads': '1']) {
        return ApplicationContext.builder()
            .properties(['spec.name': 'ExecutorRetentionSpec'] + properties)
            .beanDependencyTrackingEnabled(true)
            .retainedRegistrations(retained)
            .start()
    }
}
