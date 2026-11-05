/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.errors

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.RouteExecutor
import io.micronaut.http.server.exceptions.ExceptionHandler
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

class HandledExceptionLogLevelSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                           : 'HandledExceptionLogLevelSpec',
            'micronaut.server.log-handled-exceptions': true
    ])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/10520")
    void "an exception a handler maps to a 4xx response is not logged at ERROR"() {
        given:
        def appender = attach()

        when:
        client.toBlocking().exchange(HttpRequest.GET('/handled-exception-log/rejected'))

        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.UNAUTHORIZED.code

        and: 'the handled rejection is not an unexpected error'
        appender.events.every { it.level != Level.ERROR }

        cleanup:
        detach(appender)
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/10520")
    void "an exception a handler maps to a 5xx response is still logged at ERROR"() {
        given:
        def appender = attach()

        when:
        client.toBlocking().exchange(HttpRequest.GET('/handled-exception-log/failed'))

        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code

        and: 'a genuine server error is still an unexpected error'
        appender.events.any { it.level == Level.ERROR }

        cleanup:
        detach(appender)
    }

    private static TestAppender attach() {
        def appender = new TestAppender()
        Logger logger = (Logger) LoggerFactory.getLogger(RouteExecutor)
        logger.level = Level.DEBUG
        logger.addAppender(appender)
        appender.start()
        appender
    }

    private static void detach(TestAppender appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(RouteExecutor)
        logger.detachAppender(appender)
        appender.stop()
    }

    static class TestAppender extends AppenderBase<ILoggingEvent> {
        final List<ILoggingEvent> events = [].asSynchronized()

        @Override
        protected void append(ILoggingEvent e) {
            events << e
        }
    }

    static class RejectedException extends RuntimeException {}

    static class FailedException extends RuntimeException {}

    @Requires(property = "spec.name", value = "HandledExceptionLogLevelSpec")
    @Controller('/handled-exception-log')
    static class HandledExceptionController {
        @Get('/rejected')
        String rejected() {
            throw new RejectedException()
        }

        @Get('/failed')
        String failed() {
            throw new FailedException()
        }
    }

    @Requires(property = "spec.name", value = "HandledExceptionLogLevelSpec")
    @Singleton
    static class RejectedExceptionHandler implements ExceptionHandler<RejectedException, HttpResponse<?>> {
        @Override
        HttpResponse<?> handle(HttpRequest request, RejectedException exception) {
            HttpResponse.unauthorized()
        }
    }

    @Requires(property = "spec.name", value = "HandledExceptionLogLevelSpec")
    @Singleton
    static class FailedExceptionHandler implements ExceptionHandler<FailedException, HttpResponse<?>> {
        @Override
        HttpResponse<?> handle(HttpRequest request, FailedException exception) {
            HttpResponse.serverError()
        }
    }
}
