package io.micronaut.http.client

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.ApplicationContext
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import org.slf4j.LoggerFactory
import spock.lang.Specification

class HttpClientTraceLoggingSpec extends Specification {
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

        def ctx = ApplicationContext.run()
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI, configuration).toBlocking()

        when:
        client.exchange("/get-full")
        then:
        appender.list.formattedMessage.any { it.contains('foo') }

        cleanup:
        client.close()
        server.stop()
        ctx.stop()
        appender.stop()
    }

    @Controller
    static class Ctrl {
        @Get("/get-full")
        def getFull() {
            return 'foo'
        }
    }
}
