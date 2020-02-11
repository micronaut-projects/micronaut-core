package io.micronaut.http.netty.channel

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.netty.channel.EventLoopGroup
import io.netty.util.NettyRuntime
import spock.lang.Specification

class EventLoopGroupSpec extends Specification {

    void "test default event loop group"() {
        given:
        def context = ApplicationContext.run()

        when:
        def eventLoopGroup = context.getBean(EventLoopGroup)

        then:
        !eventLoopGroup.isTerminated()
        eventLoopGroup.executorCount() == NettyRuntime.availableProcessors() * 2

        when:
        context.close()

        then:
        eventLoopGroup.isShuttingDown()
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
        eventLoopGroup.executorCount() == NettyRuntime.availableProcessors() * 2

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
}
