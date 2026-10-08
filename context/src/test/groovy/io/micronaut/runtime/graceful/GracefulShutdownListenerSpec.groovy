package io.micronaut.runtime.graceful

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class GracefulShutdownListenerSpec extends Specification {

    def "the graceful shutdown runs once and later calls reuse it"() {
        given:
        def ctx = run('10s')
        def capable = ctx.getBean(ControlledCapable)
        def listener = ctx.getBean(GracefulShutdownListener)

        when:
        def first = CompletableFuture.runAsync { listener.shutdownGracefully() }
        TimeUnit.MILLISECONDS.sleep(200)

        then: 'the first call waits for the shutdown'
        !first.isDone()
        capable.calls.get() == 1

        when:
        capable.future.complete(null)
        first.get(10, TimeUnit.SECONDS)
        listener.shutdownGracefully()
        ctx.stop()

        then: 'neither a second call nor the ShutdownEvent starts another shutdown'
        capable.calls.get() == 1

        cleanup:
        ctx?.close()
    }

    def "a failed graceful shutdown does not fail the callers"() {
        given:
        def ctx = run('10s')
        def capable = ctx.getBean(ControlledCapable)
        def listener = ctx.getBean(GracefulShutdownListener)
        capable.future.completeExceptionally(new IllegalStateException('broken'))

        when:
        listener.shutdownGracefully()
        listener.shutdownGracefully()
        ctx.stop()

        then:
        noExceptionThrown()
        capable.calls.get() == 1

        cleanup:
        ctx?.close()
    }

    def "the grace period is measured from the start of the shutdown"() {
        given:
        def ctx = run('300ms')
        def capable = ctx.getBean(ControlledCapable)
        def listener = ctx.getBean(GracefulShutdownListener)

        when:
        long start = System.nanoTime()
        listener.shutdownGracefully()
        long firstMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        start = System.nanoTime()
        listener.shutdownGracefully()
        long secondMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)

        then: 'the first call waits for the grace period, the second finds it has passed'
        firstMillis >= 250
        secondMillis < 250
        capable.calls.get() == 1

        cleanup:
        capable?.future?.complete(null)
        ctx?.close()
    }

    def "an interrupted wait returns and keeps the interrupt"() {
        given:
        def ctx = run('10s')
        def capable = ctx.getBean(ControlledCapable)
        def listener = ctx.getBean(GracefulShutdownListener)
        boolean interrupted = false

        when:
        def thread = Thread.start {
            listener.shutdownGracefully()
            interrupted = Thread.currentThread().isInterrupted()
        }
        TimeUnit.MILLISECONDS.sleep(200)
        thread.interrupt()
        thread.join(10_000)

        then:
        !thread.isAlive()
        interrupted

        cleanup:
        capable?.future?.complete(null)
        ctx?.close()
    }

    private static ApplicationContext run(String gracePeriod) {
        ApplicationContext.run([
                'spec.name'                                         : 'GracefulShutdownListenerSpec',
                (GracefulShutdownConfiguration.ENABLED)             : true,
                'micronaut.lifecycle.graceful-shutdown.grace-period': gracePeriod,
        ])
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'GracefulShutdownListenerSpec')
    static class ControlledCapable implements GracefulShutdownCapable {
        final CompletableFuture<Object> future = new CompletableFuture<>()
        final AtomicInteger calls = new AtomicInteger()

        @Override
        CompletionStage<?> shutdownGracefully() {
            calls.incrementAndGet()
            return future
        }
    }
}
