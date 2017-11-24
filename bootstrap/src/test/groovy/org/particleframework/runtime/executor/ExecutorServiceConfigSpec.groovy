/*
 * Copyright 2017 original authors
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
package org.particleframework.runtime.executor

import org.particleframework.context.ApplicationContext
import org.particleframework.context.env.PropertySource
import org.particleframework.inject.qualifiers.Qualifiers
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
        ApplicationContext ctx = ApplicationContext.build("test")
                          .environment {
            it.addPropertySource(PropertySource.of(
                    'particle.server.executors.one.type':'FIXED',
                    'particle.server.executors.one.nThreads':'5',
                    'particle.server.executors.two.type':'work_stealing',
            ))
        }.start()

        when:
        def configs = ctx.getBeansOfType(ExecutorConfiguration)

        then:
        configs.size() == 3

        when:
        Collection<ExecutorService> executorServices = ctx.getBeansOfType(ExecutorService.class)

        then:
        executorServices.size() == 3

        when:
        ThreadPoolExecutor poolExecutor = ctx.getBean(ThreadPoolExecutor, Qualifiers.byName("one"))
        ForkJoinPool forkJoinPool = ctx.getBean(ForkJoinPool)

        then:
        executorServices.size() == 3
        poolExecutor.corePoolSize == 5
        ctx.getBean(ExecutorService.class, Qualifiers.byName("io")) // the default IO executor
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
        ApplicationContext ctx = ApplicationContext.build(environment)
                .environment {
            it.addPropertySource(PropertySource.of(
                    'particle.server.executors.one.type':'FIXED',
                    'particle.server.executors.one.nThreads':'5',
                    'particle.server.executors.two.type':'work_stealing',
            ))
        }.start()



        when:
        Collection<ExecutorService> executorServices = ctx.getBeansOfType(ExecutorService.class)
        ThreadPoolExecutor poolExecutor = ctx.getBean(ThreadPoolExecutor, Qualifiers.byName("one"))
        ForkJoinPool forkJoinPool = ctx.getBean(ForkJoinPool)

        then:
        executorServices.size() == 3
        poolExecutor.corePoolSize == 5
        ctx.getBean(ExecutorService.class, Qualifiers.byName("io")) instanceof ThreadPoolExecutor
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
        executorServices.size() == 3
        moreConfigs.size() == 3
        configs.size() == 3

        where:
        invalidateCache | environment
        true            | "test"
        false           | "test"
    }

    @Unroll
    void "test configure existing IO executor - distinct initialization order with invalidate cache: #invalidateCache"() {
        given:
        ApplicationContext ctx = ApplicationContext.build(environment)
                .environment {
            it.addPropertySource(PropertySource.of(
                    'particle.server.executors.io.type':'FIXED',
                    'particle.server.executors.io.nThreads':'5',
                    'particle.server.executors.two.type':'work_stealing',
            ))
        }.start()



        when:
        Collection<ExecutorService> executorServices = ctx.getBeansOfType(ExecutorService.class)

        then:
        executorServices.size() == 2
        ctx.getBean(ExecutorService.class, Qualifiers.byName("io")) instanceof ThreadPoolExecutor

        when:
        if(invalidateCache) {
            ctx.invalidateCaches()
        }
        def configs = ctx.getBeansOfType(UserExecutorConfiguration)
        def moreConfigs = ctx.getBeansOfType(ExecutorConfiguration)
        executorServices = ctx.getBeansOfType(ExecutorService)

        then:
        executorServices.size() == 2
        moreConfigs.size() == 2
        configs.size() == 2

        where:
        invalidateCache | environment
        true            | "test"
        false           | "test"
    }
}
