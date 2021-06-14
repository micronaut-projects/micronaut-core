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
package io.micronaut.runtime.executor

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.executor.ExecutorConfiguration
import io.micronaut.scheduling.executor.UserExecutorConfiguration
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.ExecutorService
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadPoolExecutor

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ExecutorServiceConfigSpec extends Specification {

    @Unroll
    void "test configure custom executor with invalidate cache: #invalidateCache"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'micronaut.executors.one.type':'FIXED',
                'micronaut.executors.one.nThreads':'5',
                'micronaut.executors.two.type':'work_stealing'
        )

        when:
        def configs = ctx.getBeansOfType(ExecutorConfiguration)

        then:
        configs.size() == 4

        when:
        Collection<ExecutorService> executorServices = ctx.getBeansOfType(ExecutorService.class)

        then:
        executorServices.size() == 4

        when:
        ThreadPoolExecutor poolExecutor = ctx.getBean(ThreadPoolExecutor, Qualifiers.byName("one"))
        ExecutorService forkJoinPool = ctx.getBean(ExecutorService, Qualifiers.byName("two"))

        then:
        forkJoinPool instanceof ForkJoinPool
        executorServices.size() == 4
        poolExecutor.corePoolSize == 5
        ctx.getBean(ExecutorService.class, Qualifiers.byName(TaskExecutors.IO)) // the default IO executor
        ctx.getBean(ExecutorService.class, Qualifiers.byName(TaskExecutors.SCHEDULED)) // the default IO executor
        forkJoinPool == ctx.getBean(ExecutorService.class, Qualifiers.byName("two"))
        poolExecutor == ctx.getBean(ExecutorService.class, Qualifiers.byName("one"))

        when:
        if(invalidateCache) {
            ctx.invalidateCaches()
        }

        def secondResolveExecutors = ctx.getBeansOfType(ExecutorService.class)
        def secondResolveExecutorConfig = ctx.getBeansOfType(ExecutorConfiguration.class)

        then:
        secondResolveExecutors.size() == executorServices.size()
        secondResolveExecutorConfig.size() == configs.size()
        executorServices.containsAll(secondResolveExecutors)
        configs.containsAll(secondResolveExecutorConfig)

        when:
        ctx.stop()

        then:
        poolExecutor.isShutdown()
        forkJoinPool.isShutdown()

        where:
        invalidateCache | environment
        true            | "test"
//        false           | "test"
    }


    @Unroll
    void "test configure custom executor - distinct initialization order with invalidate cache: #invalidateCache"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'micronaut.executors.one.type':'FIXED',
                'micronaut.executors.one.nThreads':'5',
                'micronaut.executors.two.type':'work_stealing'
        )



        when:
        Collection<ExecutorService> executorServices = ctx.getBeansOfType(ExecutorService.class)
        ThreadPoolExecutor poolExecutor = ctx.getBean(ThreadPoolExecutor, Qualifiers.byName("one"))
        ExecutorService forkJoinPool = ctx.getBean(ExecutorService, Qualifiers.byName("two"))

        then:
        executorServices.size() == 4
        poolExecutor.corePoolSize == 5
        ctx.getBean(ExecutorService.class, Qualifiers.byName(TaskExecutors.IO)) instanceof ThreadPoolExecutor
        ctx.getBean(ExecutorService.class, Qualifiers.byName(TaskExecutors.SCHEDULED)) instanceof ScheduledExecutorService
        forkJoinPool == ctx.getBean(ExecutorService.class, Qualifiers.byName("two"))
        poolExecutor == ctx.getBean(ExecutorService.class, Qualifiers.byName("one"))

        when:
        if(invalidateCache) {
            ctx.invalidateCaches()
        }
        def configs = ctx.getBeansOfType(UserExecutorConfiguration)
        def moreConfigs = ctx.getBeansOfType(ExecutorConfiguration)
        executorServices = ctx.getBeansOfType(ExecutorService)

        then:
        executorServices.size() == 4
        moreConfigs.size() == 4
        configs.size() == 2

        when:
        if(invalidateCache) {
            ctx.invalidateCaches()
        }

        def secondResolveExecutors = ctx.getBeansOfType(ExecutorService.class)
        def secondResolveExecutorConfig = ctx.getBeansOfType(ExecutorConfiguration.class)

        then:
        secondResolveExecutors.size() == executorServices.size()
        secondResolveExecutorConfig.size() == moreConfigs.size()
        executorServices.containsAll(secondResolveExecutors)
        moreConfigs.containsAll(secondResolveExecutorConfig)

        where:
        invalidateCache | environment
        true            | "test"
        false           | "test"
    }

    @Unroll
    void "test configure existing IO executor - distinct initialization order with invalidate cache: #invalidateCache"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'micronaut.executors.io.type':'FIXED',
                'micronaut.executors.io.nThreads':'5',
                'micronaut.executors.two.type':'work_stealing'
        )



        when:
        Collection<ExecutorService> executorServices = ctx.getBeansOfType(ExecutorService.class)

        then:
        executorServices.size() == 3
        ctx.getBean(ExecutorService.class, Qualifiers.byName(TaskExecutors.IO)) instanceof ThreadPoolExecutor

        when:
        if(invalidateCache) {
            ctx.invalidateCaches()
        }
        def configs = ctx.getBeansOfType(UserExecutorConfiguration)
        def moreConfigs = ctx.getBeansOfType(ExecutorConfiguration)
        executorServices = ctx.getBeansOfType(ExecutorService)

        then:
        executorServices.size() == 3
        moreConfigs.size() == 3
        configs.size() == 2

        where:
        invalidateCache | environment
        true            | "test"
        false           | "test"
    }
}
