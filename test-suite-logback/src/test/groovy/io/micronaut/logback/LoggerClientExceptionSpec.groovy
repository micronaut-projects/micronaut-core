package io.micronaut.logback

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.interceptor.HttpClientIntroductionAdvice
import io.micronaut.logback.clients.TeapotClient
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import reactor.test.StepVerifier
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CompletionException

@MicronautTest
@Issue("https://github.com/micronaut-projects/micronaut-core/issues/10585")
class LoggerClientExceptionSpec extends Specification {
    public static final String LOG_MESSAGE = "Client [io.micronaut.logback.clients.TeapotClient] received HTTP error response: Client '/teapot': Client Error (418)"

    @Inject
    TeapotClient client

    @Shared
    Logger log = (Logger) LoggerFactory.getLogger(HttpClientIntroductionAdvice)

    @Shared
    ListAppender<ILoggingEvent> appender = new ListAppender<>()

    void setupSpec() {
        log.setLevel(Level.TRACE)
        appender.start()
        log.addAppender(appender)
    }

    void cleanup() {
        appender.list.clear()
    }

    private void assertLogMessage() {
        assert appender.list.size() == 1
        assert appender.list[0].formattedMessage == LOG_MESSAGE
        assert appender.list[0].level == Level.DEBUG
    }

    void "client synchronous response type should log received error"() {
        when:
        def response = client.syncTeapot()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.status == HttpStatus.I_AM_A_TEAPOT
        response == null
        assertLogMessage()
    }

    void "client with completable future response type should log received error"() {
        given:
        def response = client.asyncTeapot()

        when:
        response.join()

        then:
        thrown(CompletionException)
        response.isCompletedExceptionally()
        assertLogMessage()
    }

    void "client with reactive response type should log received error"() {
        given:
        def response = client.reactiveTeapot()

        when:
        StepVerifier.create(response)
                .expectErrorMatches { it instanceof HttpClientResponseException &&
                        it.status == HttpStatus.I_AM_A_TEAPOT }
                .verify()

        then:
        assertLogMessage()
    }
}
