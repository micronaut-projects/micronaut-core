package io.micronaut.logback

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.logback.controllers.HelloWorldController
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import spock.lang.Issue
import spock.lang.Specification

@MicronautTest
@Property(name = "logger.levels.io.micronaut.logback.controllers", value = "TRACE")
@Issue("https://github.com/micronaut-projects/micronaut-core/issues/8678")
class LoggerLevelSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    void "logback can be configured via properties"() {
        given:
        MemoryAppender appender = new MemoryAppender()
        Logger l = (Logger) LoggerFactory.getLogger("io.micronaut.logback.controllers")
        l.addAppender(appender)
        appender.start()

        when:
        def response = client.toBlocking().retrieve("/", String)

        then: 'response is as expected'
        response == HelloWorldController.RESPONSE

        and: 'log message is emitted'
        appender.events == [HelloWorldController.LOG_MESSAGE]

        cleanup:
        appender.stop()
    }
}
