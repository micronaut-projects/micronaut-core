package io.micronaut.management.health.aggregator

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.slf4j.LoggerFactory
import spock.lang.Specification

import static java.util.Collections.sort

class HealthAggregatorSpec extends Specification {

    void "test that only log statements for level #logLevel are emitted"(Level logLevel) {
        given:
        MemoryAppender appender = new MemoryAppender()
        Logger l = (Logger) LoggerFactory.getLogger(DefaultHealthAggregator)
        l.setLevel(logLevel)
        l.addAppender(appender)

        when:
        appender.start()
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'HealthAggregatorSpec',
                'micronaut.application.name': 'foo',
                'endpoints.health.sensitive': false,
                'datasources.one.url': 'jdbc:h2:mem:oneDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE'
        ])
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())
        client.toBlocking().exchange("/health", Map)

        then:
        appender
        sort(appender.events)

        // these checks will break if we add log statements to DefaultHealthAggregator
        switch (logLevel) {
            case Level.INFO:
                assert appender.events.isEmpty()
                break
            case Level.DEBUG:
                assert appender.events.size() == 9
                assert appender.events[0] == 'Health result for compositeDiscoveryClient(): status UP'
                assert appender.events[1] == 'Health result for deadlockedThreads: status UP'
                assert appender.events[2] == 'Health result for diskSpace: status UP'
                assert appender.events[3] == 'Health result for gracefulShutdown: status UP'
                assert appender.events[4] == 'Health result for jdbc: status UP'
                assert appender.events[5] == 'Health result for jdbc:h2:mem:oneDb: status UP'
                assert appender.events[6] == 'Health result for liveness: status UP'
                assert appender.events[7] == 'Health result for readiness: status UP'
                assert appender.events[8] == 'Health result for service: status UP'
                break
            case Level.TRACE:
                assert appender.events.size() == 9
                assert appender.events[0].contains('Health result for compositeDiscoveryClient(): status UP, details {')
                assert appender.events[1] == 'Health result for deadlockedThreads: status UP, details {}'
                assert appender.events[2].contains('Health result for diskSpace: status UP, details {')
                assert appender.events[3].contains('Health result for gracefulShutdown: status UP, details ')
                assert appender.events[4].contains('Health result for jdbc: status UP, details {')
                assert appender.events[5].contains('Health result for jdbc:h2:mem:oneDb: status UP, details {')
                assert appender.events[6] == 'Health result for liveness: status UP, details {}'
                assert appender.events[7] == 'Health result for readiness: status UP, details {}'
                assert appender.events[8] == 'Health result for service: status UP, details {}'
                break
        }

        cleanup:
        embeddedServer.stop()

        where:
        logLevel << [Level.INFO, Level.DEBUG, Level.TRACE]
    }

    @Requires(property = 'spec.name', value = 'HealthAggregatorSpec')
    class MemoryAppender extends AppenderBase<ILoggingEvent> {
        List<String> events = []

        @Override
        protected void append(ILoggingEvent e) {
            events << e.formattedMessage
        }
    }
}
