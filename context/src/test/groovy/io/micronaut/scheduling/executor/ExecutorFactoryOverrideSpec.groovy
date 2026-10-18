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
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/**
 * A bean that replaces the executor factory and overrides {@link ExecutorFactory#executorService(ExecutorConfiguration)}
 * still creates the executor services: the producer method calls it.
 */
class ExecutorFactoryOverrideSpec extends Specification {

    void "a replacing factory's override of the single-argument method creates the executor services"() {
        given:
        OverridingExecutorFactory.CREATED.clear()
        ApplicationContext context = ApplicationContext.run(['spec.name': 'ExecutorFactoryOverrideSpec', 'spec.variant': 'plain',
                                                             'micronaut.executors.custom.type': 'fixed', 'micronaut.executors.custom.number-of-threads': '1'])

        when:
        ExecutorService executor = context.getBean(ExecutorService, Qualifiers.byName("custom"))

        then: "the override was called, and the thread factory of the configuration's name was used"
        'custom' in OverridingExecutorFactory.CREATED
        context.getBeansOfType(ExecutorService, Qualifiers.byName("custom")).size() == 1
        executor.submit({ Thread.currentThread().name } as Callable<String>).get(10, TimeUnit.SECONDS).startsWith("custom-executor")

        cleanup:
        context.close()
    }

    void "a replacing factory without a bean locator whose override calls the overridden method gets the thread factory of the configuration's name"() {
        given:
        ContextFreeOverridingExecutorFactory.CREATED.clear()
        ApplicationContext context = ApplicationContext.run(['spec.name': 'ExecutorFactoryOverrideSpec', 'spec.variant': 'context-free',
                                                             'micronaut.executors.custom.type': 'fixed', 'micronaut.executors.custom.number-of-threads': '1'])

        when:
        ExecutorService executor = context.getBean(ExecutorService, Qualifiers.byName("custom"))

        then:
        context.getBeansOfType(ExecutorService, Qualifiers.byName("custom")).size() == 1
        'custom' in ContextFreeOverridingExecutorFactory.CREATED
        executor.submit({ Thread.currentThread().name } as Callable<String>).get(10, TimeUnit.SECONDS).startsWith("custom-executor")

        cleanup:
        context.close()
    }
}
