package io.micronaut.http.server.netty

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.context.event.HttpRequestReceivedEvent
import io.micronaut.http.context.event.HttpRequestTerminatedEvent
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Request event listeners run on the event loop with the default thread selection. A listener
 * that takes too long there is reported at debug level.
 */
class SlowRequestEventListenerSpec extends Specification {
    private static final String LOGGER = RoutingInBoundHandler.class.name

    def "slow request event listeners on the event loop are reported at debug level"(String threadSelection, long delayMillis, boolean expectReport) {
        given:
        def logger = (Logger) LoggerFactory.getLogger(LOGGER)
        def previousLevel = logger.level
        def appender = new ListAppender()
        appender.start()
        logger.addAppender(appender)
        logger.level = Level.DEBUG
        def ctx = ApplicationContext.run([
                'spec.name'                           : 'SlowRequestEventListenerSpec',
                'slow-listener.delay-millis'          : delayMillis,
                'micronaut.server.thread-selection'   : threadSelection,
        ])
        def server = ctx.getBean(EmbeddedServer).start()
        def client = ctx.createBean(HttpClient, server.URI)
        def listener = ctx.getBean(SlowListener)

        when:
        def response = client.toBlocking().retrieve('/slow-listener')

        then:
        response == 'ok'
        new PollingConditions(timeout: 10).eventually {
            listener.terminated.size() == 1
        }
        (listener.received + listener.terminated).every { it.contains('eventLoopGroup') == (threadSelection != 'BLOCKING') }

        when: 'the report is logged right after the listener returns'
        Thread.sleep(200)

        then:
        appender.messages.count { it.contains('HttpRequestReceivedEvent') && it.contains('took') } == (expectReport ? 1 : 0)
        appender.messages.count { it.contains('HttpRequestTerminatedEvent') && it.contains('took') } == (expectReport ? 1 : 0)

        cleanup:
        client.close()
        ctx.close()
        logger.detachAppender(appender)
        logger.level = previousLevel

        where:
        threadSelection | delayMillis | expectReport
        'MANUAL'        | 250         | true
        'AUTO'          | 250         | true
        'MANUAL'        | 0           | false
        'BLOCKING'      | 250         | false
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'SlowRequestEventListenerSpec')
    static class SlowListener {
        final long delayMillis
        final List<String> received = new CopyOnWriteArrayList<>()
        final List<String> terminated = new CopyOnWriteArrayList<>()

        SlowListener(@io.micronaut.context.annotation.Property(name = 'slow-listener.delay-millis') long delayMillis) {
            this.delayMillis = delayMillis
        }

        @EventListener
        void onReceived(HttpRequestReceivedEvent event) {
            Thread.sleep(delayMillis)
            received.add(Thread.currentThread().name)
        }

        @EventListener
        void onTerminated(HttpRequestTerminatedEvent event) {
            Thread.sleep(delayMillis)
            terminated.add(Thread.currentThread().name)
        }
    }

    @Controller('/slow-listener')
    @Requires(property = 'spec.name', value = 'SlowRequestEventListenerSpec')
    static class TestController {
        @Get
        String get() {
            return 'ok'
        }
    }

    static class ListAppender extends AppenderBase<ILoggingEvent> {
        final List<String> messages = new CopyOnWriteArrayList<>()

        @Override
        protected void append(ILoggingEvent event) {
            messages.add(event.formattedMessage)
        }
    }
}
