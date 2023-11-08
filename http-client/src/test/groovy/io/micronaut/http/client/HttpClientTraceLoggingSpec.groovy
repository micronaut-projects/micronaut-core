package io.micronaut.http.client

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import org.slf4j.LoggerFactory
import spock.lang.AutoCleanup
import spock.lang.Specification

class HttpClientTraceLoggingSpec extends Specification {

    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'HttpClientTraceLoggingSpec'
    ])

    def 'full response'() {
        given:
        String loggerName = 'io.micronaut.http.client.HttpClientTraceLoggingSpec.' + UUID.randomUUID()
        def appender = new ListAppender<ILoggingEvent>()
        Logger l = (Logger) LoggerFactory.getLogger(loggerName)
        l.addAppender(appender)
        l.setLevel(Level.ALL)
        appender.start()

        def configuration = new DefaultHttpClientConfiguration()
        configuration.setLoggerName(loggerName)

        def client = server.applicationContext.createBean(HttpClient, server.URI, configuration).toBlocking()

        when:
        client.exchange("/get-full")

        then:
        appender.list.formattedMessage.any { it.contains('foo') }

        cleanup:
        client.close()
        appender.stop()
    }

    @Controller
    @Requires(property = 'spec.name', value = 'HttpClientTraceLoggingSpec')
    static class Ctrl {

        @Get("/get-full")
        def getFull() {
            return 'foo'
        }
    }
}
