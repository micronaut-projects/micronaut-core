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
@Property(name = "logger.levels.io.micronaut.logback", value = "INFO")
@Property(name = "endpoints.loggers.enabled", value = "true")
@Property(name = "endpoints.loggers.sensitive", value = "false")
@Property(name = "endpoints.loggers.write-sensitive", value = "false")
@Issue("https://github.com/micronaut-projects/micronaut-core/issues/8679")
class LoggerEndpointSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    void "logback configuration from properties is as expected"() {
        when:
        def response = client.toBlocking().retrieve("/loggers/io.micronaut.logback")

        then:
        response.contains("INFO")
    }

    void "logback can be configured"() {
        given:
        MemoryAppender appender = new MemoryAppender()
        Logger l = (Logger) LoggerFactory.getLogger("io.micronaut.logback.controllers")
        l.addAppender(appender)
        appender.start()

        when:
        def response = client.toBlocking().retrieve("/", String)

        then: 'response is as expected'
        response == HelloWorldController.RESPONSE

        and: 'no log message is emitted'
        appender.events.empty

        when: 'log level is changed to TRACE'
        def body = '{ "configuredLevel": "TRACE" }'
        def post = HttpRequest.POST("/loggers/io.micronaut.logback.controllers", body).contentType(MediaType.APPLICATION_JSON_TYPE)
        client.toBlocking().exchange(post)

        and:
        response = client.toBlocking().retrieve("/", String)

        then: 'response is as expected'
        response == HelloWorldController.RESPONSE

        and: 'log message is emitted'
        appender.events == [HelloWorldController.LOG_MESSAGE]

        cleanup:
        appender.stop()
    }
}
