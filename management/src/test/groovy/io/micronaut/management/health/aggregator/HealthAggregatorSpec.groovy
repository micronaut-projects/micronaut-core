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
        Logger l = (Logger) LoggerFactory.getLogger(DefaultHealthAggregator.class.name)
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

        appender.events.isEmpty() == (logLevel == Level.INFO)

        if (logLevel in [Level.DEBUG, Level.TRACE]) {
            appender.events[0] == 'Health result for compositeDiscoveryClient(): status UP'
            appender.events[1] == 'Health result for diskSpace: status UP'
            appender.events[2] == 'Health result for jdbc: status UP'
            appender.events[3] == 'Health result for jdbc:h2:mem:oneDb: status UP'
            appender.events[4] == 'Health result for liveness: status UP'
            appender.events[5] == 'Health result for readiness: status UP'
            appender.events[6] == 'Health result for service: status UP'
        }

        if (logLevel == Level.TRACE) {
            appender.events[0].contains('Health result for compositeDiscoveryClient(): status UP, details {')
            appender.events[1].contains('Health result for diskSpace: status UP, details {')
            appender.events[2].contains('Health result for jdbc: status UP, details {')
            appender.events[3].contains('Health result for jdbc:h2:mem:oneDb: status UP, details {')
            appender.events[4] == 'Health result for liveness: status UP, details {}'
            appender.events[5] == 'Health result for readiness: status UP, details {}'
            appender.events[6] == 'Health result for service: status UP, details {}'
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
