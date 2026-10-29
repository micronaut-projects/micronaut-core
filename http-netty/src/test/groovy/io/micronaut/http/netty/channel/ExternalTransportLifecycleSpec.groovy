package io.micronaut.http.netty.channel

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.netty.channel.IoHandle
import io.netty.channel.IoHandler
import io.netty.channel.IoHandlerContext
import io.netty.channel.IoHandlerFactory
import io.netty.channel.IoRegistration
import io.netty.channel.nio.NioIoHandler
import io.netty.util.concurrent.ThreadAwareExecutor
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ExternalTransportLifecycleSpec extends Specification {
    def 'io handler destruction follows active work and precedes group termination'() {
        given:
        def initialized = new AtomicBoolean()
        def destroyed = new AtomicBoolean()
        def started = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def factory = new EventLoopGroupFactory() {
            @Override
            IoHandlerFactory createIoHandlerFactory(EventLoopGroupConfiguration configuration) {
                new IoHandlerFactory() {
                    @Override
                    IoHandler newHandler(ThreadAwareExecutor executor) {
                        def delegate = NioIoHandler.newFactory().newHandler(executor)
                        new IoHandler() {
                            @Override
                            void initialize() { delegate.initialize(); initialized.set(true) }
                            @Override
                            int run(IoHandlerContext handlerContext) { delegate.run(handlerContext) }
                            @Override
                            void prepareToDestroy() { delegate.prepareToDestroy() }
                            @Override
                            void destroy() { delegate.destroy(); destroyed.set(true) }
                            @Override
                            IoRegistration register(IoHandle handle) { delegate.register(handle) }
                            @Override
                            void wakeup() { delegate.wakeup() }
                            @Override
                            boolean isCompatible(Class<? extends IoHandle> type) { delegate.isCompatible(type) }
                        }
                    }
                }
            }
        }
        def context = ApplicationContext.builder().properties([
            'micronaut.netty.event-loops.external.num-threads': 1,
            'micronaut.netty.event-loops.external.transport': ['fixture'],
            'micronaut.netty.event-loops.external.shutdown-quiet-period': '0ms',
            'micronaut.netty.event-loops.external.shutdown-timeout': '1s',
        ]).build()
        context.registerSingleton(EventLoopGroupFactory, factory, Qualifiers.byName('fixture'))
        context.start()
        def group = context.getBean(EventLoopGroupRegistry).getEventLoopGroup('external').orElseThrow()
        def work = group.next().submit { started.countDown(); release.await() }
        assert started.await(10, TimeUnit.SECONDS)

        when:
        context.close()

        then:
        initialized.get()
        !destroyed.get()

        when:
        release.countDown()
        work.sync()
        group.terminationFuture().sync()

        then:
        destroyed.get()
        group.isTerminated()

        cleanup:
        release.countDown()
        context.close()
    }
}
