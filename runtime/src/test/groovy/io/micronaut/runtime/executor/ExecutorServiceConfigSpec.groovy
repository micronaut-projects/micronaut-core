package io.micronaut.runtime.executor

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.scheduling.LoomSupport
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.executor.ExecutorConfiguration
import io.micronaut.scheduling.executor.UserExecutorConfiguration
import io.micronaut.scheduling.instrument.InstrumentedExecutor
import spock.lang.Specification

import java.util.concurrent.ExecutorService
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadPoolExecutor

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ExecutorServiceConfigSpec extends Specification {
    static final int expectedExecutorCount = LoomSupport.isSupported() ? 6 : 5

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
        configs.name ==~ ['one', 'two', 'io', 'scheduled'] + (Runtime.version().feature() == 17 ? [] : ['virtual'])

        when:
        Collection<ExecutorService> executorServices = ctx.getBeansOfType(ExecutorService.class)

        then:
        executorServices.size() == expectedExecutorCount

        when:
        ThreadPoolExecutor poolExecutor = (ThreadPoolExecutor) InstrumentedExecutor.unwrap(
                ctx.getBean(ExecutorService, Qualifiers.byName("one"))
        )
        ExecutorService forkJoinPool = InstrumentedExecutor.unwrap(
                ctx.getBean(ExecutorService, Qualifiers.byName("two"))
        )

        then:
        forkJoinPool instanceof ForkJoinPool
        executorServices.size() == expectedExecutorCount
        poolExecutor.corePoolSize == 5
        ctx.getBean(ExecutorService.class, Qualifiers.byName(TaskExecutors.IO)) // the default IO executor
        ctx.getBean(ExecutorService.class, Qualifiers.byName(TaskExecutors.SCHEDULED)) // the default IO executor
        forkJoinPool == InstrumentedExecutor.unwrap(
                ctx.getBean(ExecutorService.class, Qualifiers.byName("two"))
        )
        poolExecutor == InstrumentedExecutor.unwrap(
                ctx.getBean(ExecutorService.class, Qualifiers.byName("one"))
        )

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


    void "test configure custom executor - distinct initialization order with invalidate cache: #invalidateCache"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'micronaut.executors.one.type':'FIXED',
                'micronaut.executors.one.nThreads':'5',
                'micronaut.executors.two.type':'work_stealing'
        )



        when:
        Collection<ExecutorService> executorServices = ctx.getBeansOfType(ExecutorService.class)
        ThreadPoolExecutor poolExecutor = (ThreadPoolExecutor) InstrumentedExecutor.unwrap(
                ctx.getBean(ExecutorService, Qualifiers.byName("one"))
        )
        ExecutorService forkJoinPool = InstrumentedExecutor.unwrap(
                ctx.getBean(ExecutorService, Qualifiers.byName("two"))
        )

        then:
        executorServices.size() == expectedExecutorCount
        poolExecutor.corePoolSize == 5

        def ioExecutor = InstrumentedExecutor.unwrap(
                ctx.getBean(ExecutorService.class, Qualifiers.byName(TaskExecutors.IO))
        )
        ioExecutor instanceof ThreadPoolExecutor
        ctx.getBean(ExecutorService.class, Qualifiers.byName(TaskExecutors.SCHEDULED)) instanceof ScheduledExecutorService
        forkJoinPool == InstrumentedExecutor.unwrap(
                ctx.getBean(ExecutorService.class, Qualifiers.byName("two"))
        )
        poolExecutor == InstrumentedExecutor.unwrap(
                ctx.getBean(ExecutorService.class, Qualifiers.byName("one"))
        )

        when:
        if(invalidateCache) {
            ctx.invalidateCaches()
        }
        def configs = ctx.getBeansOfType(UserExecutorConfiguration)
        def moreConfigs = ctx.getBeansOfType(ExecutorConfiguration)
        executorServices = ctx.getBeansOfType(ExecutorService)

        then:
        executorServices.size() == expectedExecutorCount
        moreConfigs.name ==~ ['one', 'two', 'io', 'scheduled'] + (Runtime.version().feature() == 17 ? [] : ['virtual'])
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
        executorServices.size() == expectedExecutorCount - 1
        InstrumentedExecutor.unwrap(
            ctx.getBean(ExecutorService.class, Qualifiers.byName(TaskExecutors.IO))
        ) instanceof ThreadPoolExecutor

        when:
        if(invalidateCache) {
            ctx.invalidateCaches()
        }
        def configs = ctx.getBeansOfType(UserExecutorConfiguration)
        def moreConfigs = ctx.getBeansOfType(ExecutorConfiguration)
        executorServices = ctx.getBeansOfType(ExecutorService)

        then:
        executorServices.size() == expectedExecutorCount - 1
        moreConfigs.name ==~ ['two', 'io', 'scheduled'] + (Runtime.version().feature() == 17 ? [] : ['virtual'])
        configs.size() == 2

        where:
        invalidateCache | environment
        true            | "test"
        false           | "test"
    }
}
