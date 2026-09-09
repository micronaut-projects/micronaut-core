package io.micronaut.http.server.netty.mdc

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.propagation.slf4j.MdcPropagationContext
import io.micronaut.core.propagation.MutablePropagatedContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.annotation.PreMatching
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN

class MdcPreMatchingFilterSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'MdcPreMatchingFilterSpec'])

    @Shared
    @AutoCleanup
    HttpClient client = HttpClient.create(embeddedServer.URL)

    void 'MDC added by a pre-matching filter is visible during route matching'() {
        given:
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory()
        def logger = lc.getLogger('io.micronaut.http.server.RequestLifecycle')
        def previousLevel = logger.getLevel()
        logger.setLevel(Level.TRACE)
        def appender = new ListAppender<ILoggingEvent>()
        appender.setContext(lc)
        appender.start()
        logger.addAppender(appender)

        when:
        String response = client.toBlocking().retrieve(HttpRequest.GET('/pre-matching-mdc'))
        def matched = appender.list.find { it.formattedMessage.startsWith('Matched route') }

        then:
        response == '1234567890'
        matched != null
        matched.MDCPropertyMap.get('trace_id') == '1234567890'

        cleanup:
        logger.detachAppender(appender)
        logger.setLevel(previousLevel)
    }

    @Controller
    @Requires(property = 'spec.name', value = 'MdcPreMatchingFilterSpec')
    static class TheController {
        @Get('/pre-matching-mdc')
        String get() {
            return MDC.get('trace_id')
        }
    }

    @ServerFilter(MATCH_ALL_PATTERN)
    @Requires(property = 'spec.name', value = 'MdcPreMatchingFilterSpec')
    static class PreMatchingMdcFilter {
        @PreMatching
        @RequestFilter
        void filterRequest(MutablePropagatedContext mutablePropagatedContext) {
            mutablePropagatedContext.add(new MdcPropagationContext(Map.of('trace_id', '1234567890')))
        }
    }
}
