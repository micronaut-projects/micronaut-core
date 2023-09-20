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
                'micronaut.http.client.read-timeout': '6000 sec',
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
            appender.events[0].contains('Health monitor check for compositeDiscoveryClient() with status')
            appender.events[1].contains('Health monitor check for diskSpace with status')
            appender.events[2].contains('Health monitor check for jdbc with status')
            appender.events[3].contains('Health monitor check for jdbc:h2:mem:oneDb with status')
            appender.events[4].contains('Health monitor check for liveness with status')
            appender.events[5].contains('Health monitor check for readiness with status')
            appender.events[6].contains('Health monitor check for service with status')
        }

        if (logLevel == Level.TRACE) {
            appender.events[7].contains('Health result for compositeDiscoveryClient() with details')
            appender.events[8].contains('Health result for diskSpace with details')
            appender.events[9].contains('Health result for jdbc with details')
            appender.events[10].contains('Health result for jdbc:h2:mem:oneDb with details')
            appender.events[11] == 'Health result for liveness with details {}'
            appender.events[12] == 'Health result for readiness with details {}'
            appender.events[13] == 'Health result for service with details {}'
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
