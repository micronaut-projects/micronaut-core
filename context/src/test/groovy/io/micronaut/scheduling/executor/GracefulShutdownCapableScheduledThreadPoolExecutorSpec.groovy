package io.micronaut.scheduling.executor

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.graceful.GracefulShutdownManager
import io.micronaut.scheduling.TaskExecutors
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class GracefulShutdownCapableScheduledThreadPoolExecutorSpec extends Specification {
    def "shutdown works"() {
        given:
        def ctx = ApplicationContext.run()
        def scheduler = (ScheduledExecutorService) ctx.getBean(ExecutorService, Qualifiers.byName(TaskExecutors.SCHEDULED))
        def lastTick = 0L

        when:
        def future = scheduler.scheduleAtFixedRate({
            lastTick = System.nanoTime()
        }, 0, 1, TimeUnit.SECONDS)
        then:
        new PollingConditions().eventually {
            lastTick != 0
        }

        when:
        def shutdownFuture = ctx.getBean(GracefulShutdownManager).shutdownGracefully()
        then:
        shutdownFuture.toCompletableFuture().get(5, TimeUnit.SECONDS) == null

        when:
        future.get(5, TimeUnit.SECONDS)
        then:
        thrown CancellationException

        cleanup:
        ctx.close()
    }
}
