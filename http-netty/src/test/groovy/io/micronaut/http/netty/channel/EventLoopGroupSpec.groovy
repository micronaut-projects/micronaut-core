package io.micronaut.http.netty.channel

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.inject.qualifiers.Qualifiers
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.EventLoopGroup
import io.netty.util.NettyRuntime
import io.netty.util.ResourceLeakDetector
import spock.lang.Specification

import java.time.Duration

class EventLoopGroupSpec extends Specification {

    void "test default allocator order"() {
        given:
        def context = ApplicationContext.run()

        when:
        context.getBean(EventLoopGroup)
        then:
        PooledByteBufAllocator.defaultMaxOrder() == 3

        cleanup:
        context.close()
    }

    void "test default event loop group"() {
        given:
        def context = ApplicationContext.run()

        when:
        def eventLoopGroup = context.getBean(EventLoopGroup)

        then:
        !eventLoopGroup.isTerminated()
        eventLoopGroup.executorCount() == NettyRuntime.availableProcessors()
        ResourceLeakDetector.level == ResourceLeakDetector.Level.DISABLED

        when:
        context.close()

        then:
        eventLoopGroup.isShuttingDown()
    }

    void "test configure resource leak detector"() {
        given:
        def context = ApplicationContext.run(
                'netty.resource-leak-detector-level':'PARANOID'
        )

        when:
        context.getBean(EventLoopGroup)

        then:
        ResourceLeakDetector.level == ResourceLeakDetector.Level.PARANOID

        cleanup:
        context.close()
    }


    void "test configure default event loop group"() {
        given:
        def context = ApplicationContext.run(
                'micronaut.netty.event-loops.default.num-threads':5
        )

        when:
        DefaultEventLoopGroupConfiguration config = context.getBean(DefaultEventLoopGroupConfiguration)

        then:
        config.numThreads == 5

        when:
        def eventLoopGroup = context.getBean(EventLoopGroup)

        then:
        !eventLoopGroup.isTerminated()
        eventLoopGroup.executorCount() == 5

        when:
        context.close()

        then:
        eventLoopGroup.isShuttingDown()
    }

    void "test core ratio"(int ratio) {
        given:
        def context = ApplicationContext.run(
                'micronaut.netty.event-loops.default.thread-core-ratio': ratio
        )

        when:
        def eventLoopGroup = context.getBean(EventLoopGroup)

        then:
        !eventLoopGroup.isTerminated()
        eventLoopGroup.executorCount() == ratio * Runtime.getRuntime().availableProcessors()

        when:
        context.close()

        then:
        eventLoopGroup.isShuttingDown()

        where:
        ratio << [1, 2, 3]
    }

    void "test configure additional event loop groups"() {
        given:
        def context = ApplicationContext.run(
                'micronaut.netty.event-loops.one.num-threads':5
        )

        when:
        DefaultEventLoopGroupConfiguration config = context.getBean(DefaultEventLoopGroupConfiguration, Qualifiers.byName('one'))

        then:
        config.numThreads == 5

        when:
        def eventLoopGroup = context.getBean(EventLoopGroup)

        then:
        !eventLoopGroup.isTerminated()
        eventLoopGroup.executorCount() == NettyRuntime.availableProcessors()

        when:
        def eventLoopGroup2 = context.getBean(EventLoopGroup, Qualifiers.byName("one"))

        then:
        !eventLoopGroup2.isTerminated()
        eventLoopGroup2.executorCount() == 5

        when:
        context.close()

        then:
        eventLoopGroup.isShuttingDown()
    }

    void "test configure shutdown for default event loop groups"() {
        given:
        ApplicationContext context = new DefaultApplicationContext("test")
        context.environment.addPropertySource(PropertySource.of("test",
                [
                    'micronaut.netty.event-loops.default.shutdown-quiet-period' : '1ms',
                    'micronaut.netty.event-loops.default.shutdown-timeout' : '100ms'
                ]
        ))
        context.start()

        when:
        DefaultEventLoopGroupConfiguration config = context.getBean(DefaultEventLoopGroupConfiguration)

        then:
        config.shutdownQuietPeriod == Duration.ofMillis(1)
        config.shutdownTimeout == Duration.ofMillis(100)
    }

    void "test configure shutdown for other event loop groups"() {
        given:
        ApplicationContext context = new DefaultApplicationContext("test")
        context.environment.addPropertySource(PropertySource.of("test",
                [
                    'micronaut.netty.event-loops.one.shutdown-quiet-period' : '10ms',
                    'micronaut.netty.event-loops.one.shutdown-timeout' : '500ms'
                ]
        ))
        context.start()

        when:
        DefaultEventLoopGroupConfiguration config = context.getBean(DefaultEventLoopGroupConfiguration, Qualifiers.byName('one'))

        then:
        config.shutdownQuietPeriod == Duration.ofMillis(10)
        config.shutdownTimeout == Duration.ofMillis(500)
    }
}
