package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.http.netty.channel.DefaultEventLoopGroupConfiguration
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration
import io.netty.channel.EventLoopGroup
import io.netty.util.NettyRuntime
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

/**
 * Tests for the event loop groups that {@link DefaultNettyEmbeddedServerFactory} creates itself,
 * i.e. the groups that are not provided by the {@code EventLoopGroupRegistry}.
 */
class EventLoopGroupCreationSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(['spec.name': 'EventLoopGroupCreationSpec'])

    private static int executorCount(EventLoopGroup group) {
        int count = 0
        for (def executor : group) {
            count++
        }
        return count
    }

    private static String threadName(EventLoopGroup group) {
        group.next().submit({ Thread.currentThread().name } as Callable<String>).get(10, TimeUnit.SECONDS)
    }

    private static EventLoopGroupConfiguration config(String name, int numThreads, double threadCoreRatio) {
        new DefaultEventLoopGroupConfiguration(name, numThreads, threadCoreRatio, null, false, null, null, null, null, false)
    }

    def 'a non-default group is named after its configuration'() {
        given:
        EventLoopGroup group = ctx.getBean(DefaultNettyEmbeddedServerFactory).createEventLoopGroup(config('custom', 2, 1.0d))

        expect:
        executorCount(group) == 2
        threadName(group).startsWith('custom-eventLoopGroup-')

        cleanup:
        group.shutdownGracefully().await(10, TimeUnit.SECONDS)
    }

    def 'the default group keeps the shared netty thread pool name'() {
        given:
        EventLoopGroup group = ctx.getBean(DefaultNettyEmbeddedServerFactory)
                .createEventLoopGroup(config(EventLoopGroupConfiguration.DEFAULT, 2, 1.0d))

        expect:
        executorCount(group) == 2
        threadName(group).startsWith('default-eventLoopGroup-')

        cleanup:
        group.shutdownGracefully().await(10, TimeUnit.SECONDS)
    }

    def 'the thread core ratio of the configuration is honoured when no thread count is set'() {
        given:
        int expected = Math.toIntExact(Math.round(0.5d * NettyRuntime.availableProcessors()))
        EventLoopGroup group = ctx.getBean(DefaultNettyEmbeddedServerFactory).createEventLoopGroup(config('ratio', 0, 0.5d))

        expect:
        expected >= 1
        executorCount(group) == expected

        cleanup:
        group.shutdownGracefully().await(10, TimeUnit.SECONDS)
    }
}
