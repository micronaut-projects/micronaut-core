package io.micronaut.http.netty.channel

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.inject.qualifiers.Qualifiers
import io.netty.channel.EventLoopGroup
import io.netty.channel.IoHandlerFactory
import io.netty.channel.nio.NioIoHandler
import io.netty.util.NettyRuntime
import io.netty.util.ResourceLeakDetector
import io.netty.util.concurrent.DefaultThreadFactory
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

class EventLoopGroupSpec extends Specification {

    void "external factories require explicit selection regardless of registration order"(boolean reverse) {
        given:
        def nio = Mock(EventLoopGroupFactory)
        def external = Mock(EventLoopGroupFactory)
        def second = Mock(EventLoopGroupFactory)
        def factories = reverse ? [second: second, external: external, nio: nio] : [nio: nio, external: external, second: second]
        def factory = new DefaultEventLoopGroupFactory(factories, null)
        def handler = io.netty.channel.nio.NioIoHandler.newFactory()

        when:
        factory.createIoHandlerFactory()

        then:
        1 * nio.createIoHandlerFactory() >> handler
        0 * external._
        0 * second._

        when:
        factory.createIoHandlerFactory(config(['missing', 'external', 'nio']))

        then:
        1 * external.createIoHandlerFactory(_) >> handler
        0 * nio._
        0 * second._

        where:
        reverse << [false, true]
    }

    void "strict selection reports unavailable transport and null configuration uses nio"() {
        given:
        def nio = Mock(EventLoopGroupFactory)
        def factory = new DefaultEventLoopGroupFactory([nio: nio], null)

        when:
        factory.createIoHandlerFactory(config(['unavailable']))

        then:
        def e = thrown(NoSuchElementException)
        e.message.contains('unavailable')

        when:
        factory.channelClass(NettyChannelType.SERVER_SOCKET, null)

        then:
        1 * nio.channelClass(NettyChannelType.SERVER_SOCKET, null)
    }

    void "deprecated native qualifier retains its priority"() {
        given:
        def nativeFactory = Mock(EventLoopGroupFactory)
        def factory = new DefaultEventLoopGroupFactory(new NioEventLoopGroupFactory(), nativeFactory)

        when:
        factory.createIoHandlerFactory()

        then:
        1 * nativeFactory.createIoHandlerFactory()
    }

    void "configuration overload retains legacy group creation overrides"(boolean throughDefault, int threads, Integer ioRatio, int expectedThreads) {
        given:
        def configuration = new DefaultEventLoopGroupConfiguration('external', threads, 0.0001, ioRatio, false, ['external'], null, null, null, false)
        def threadFactory = new DefaultThreadFactory('configured')
        def customGroup = Mock(EventLoopGroup)
        def legacyFactory = new EventLoopGroupFactory() {
            @Override
            EventLoopGroup createEventLoopGroup(int suppliedThreads, ThreadFactory suppliedThreadFactory, Integer suppliedRatio) {
                assert suppliedThreads == expectedThreads
                assert suppliedThreadFactory.is(threadFactory)
                assert suppliedRatio == ioRatio
                return customGroup
            }
        }
        def factory = throughDefault ? new DefaultEventLoopGroupFactory([external: legacyFactory], null) : legacyFactory

        expect:
        factory.createEventLoopGroup(configuration, threadFactory).is(customGroup)

        where:
        throughDefault | threads | ioRatio | expectedThreads
        false          | 3       | 37      | 3
        false          | 0       | null    | 1
        true           | 3       | 37      | 3
        true           | 0       | null    | 1
    }

    void "legacy groups and configured handlers remain independent extension points"() {
        given:
        def configuration = config(['external'])
        def threadFactory = new DefaultThreadFactory('configured')
        def customGroup = Mock(EventLoopGroup)
        def handler = NioIoHandler.newFactory()
        def external = new EventLoopGroupFactory() {
            @Override
            EventLoopGroup createEventLoopGroup(int threads, ThreadFactory supplied, Integer ioRatio) {
                assert threads == 1
                assert supplied.is(threadFactory)
                return customGroup
            }

            @Override
            IoHandlerFactory createIoHandlerFactory(EventLoopGroupConfiguration supplied) {
                assert supplied.is(configuration)
                return handler
            }
        }
        def factory = new DefaultEventLoopGroupFactory([external: external], null)

        when:
        def group = factory.createEventLoopGroup(configuration, threadFactory)

        then:
        group.is(customGroup)
        factory.createIoHandlerFactory(configuration).is(handler)

        cleanup:
        if (group != null && !group.is(customGroup)) {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync()
        }
    }

    void "selected concrete factories retain configured custom group creation"() {
        given:
        def configuration = config(['external'])
        def threadFactory = new DefaultThreadFactory('configured')
        def customGroup = Mock(EventLoopGroup)
        def external = Mock(EventLoopGroupFactory)
        def factory = new DefaultEventLoopGroupFactory([external: external], null)

        when:
        def group = factory.createEventLoopGroup(configuration, threadFactory)

        then:
        group.is(customGroup)
        1 * external.createEventLoopGroup(configuration, threadFactory) >> customGroup
        0 * external._
    }

    private static EventLoopGroupConfiguration config(List<String> transports) {
        new DefaultEventLoopGroupConfiguration('external', 1, 1, null, false, transports, null, null, null, false)
    }

    private static final String ALLOCATOR_TYPE = 'io.netty.allocator.type'
    private static final String MAX_ORDER = 'io.netty.allocator.maxOrder'

    void "test default allocator max order with allocator type #allocatorType and max order #maxOrder"() {
        given:
        def previousType = System.getProperty(ALLOCATOR_TYPE)
        def previousMaxOrder = System.getProperty(MAX_ORDER)
        def previousLevel = ResourceLeakDetector.level
        setOrClearProperty(ALLOCATOR_TYPE, allocatorType)
        setOrClearProperty(MAX_ORDER, maxOrder)
        def context = ApplicationContext.run()

        when:
        context.getBean(EventLoopGroup)

        then:
        System.getProperty(MAX_ORDER) == expectedMaxOrder

        cleanup:
        context.close()
        setOrClearProperty(ALLOCATOR_TYPE, previousType)
        setOrClearProperty(MAX_ORDER, previousMaxOrder)
        ResourceLeakDetector.level = previousLevel

        where:
        allocatorType | maxOrder | expectedMaxOrder
        null          | null     | null
        'adaptive'    | null     | null
        'unpooled'    | null     | null
        'pooled'      | null     | '3'
        'pooled'      | '5'      | '5'
        'unknown'     | null     | '3'
    }

    private static void setOrClearProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name)
        } else {
            System.setProperty(name, value)
        }
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
        ApplicationContext context = ApplicationContext.builder("test").build()
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
        ApplicationContext context = ApplicationContext.builder("test").build()
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

    void "test small thread-core-ratio yields at least one thread"() {
        given:
        def context = ApplicationContext.run(['micronaut.netty.event-loops.default.thread-core-ratio': 0.0001])

        when:
        def eventLoopGroup = context.getBean(EventLoopGroup)

        then:
        eventLoopGroup.executorCount() == 1

        cleanup:
        context.close()
    }

    void "test numThreads calculation"() {
        given:
        EventLoopGroupConfiguration config = Stub {
            getNumThreads() >> explicit
            getThreadCoreRatio() >> ratio
        }

        expect:
        DefaultEventLoopGroupRegistry.numThreads(config) == expected

        where:
        explicit | ratio  | expected
        0        | 0.0001 | 1
        3        | 0.0001 | 3
        0        | 0      | 0
        0        | 1.0    | NettyRuntime.availableProcessors()
    }
}
