/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.retry.intercept

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.ApplicationContext
import io.micronaut.retry.annotation.Fallback
import io.micronaut.retry.annotation.Recoverable
import io.micronaut.retry.exception.FallbackException
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import spock.lang.Issue
import spock.lang.Specification

@Issue("https://github.com/micronaut-projects/micronaut-core/issues/13276")
class RecoveryInterceptorLoggingSpec extends Specification {

    Logger logger = (Logger) LoggerFactory.getLogger(RecoveryInterceptor)
    Level originalLevel
    ListAppender<ILoggingEvent> appender

    void setup() {
        originalLevel = logger.level
        logger.level = Level.ALL
        appender = new ListAppender<>()
        appender.start()
        logger.addAppender(appender)
    }

    void cleanup() {
        logger.detachAppender(appender)
        appender.stop()
        logger.level = originalLevel
    }

    void "handled fallback is not logged at error"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        String result = context.getBean(HandledService).execute()

        then:
        result == "fallback"
        appender.list.findAll { it.level == Level.ERROR }.isEmpty()
        ILoggingEvent event = appender.list.find { it.level == Level.DEBUG && it.formattedMessage.contains("resolved fallback") }
        event != null
        event.throwableProxy == null

        cleanup:
        context.close()
    }

    void "missing fallback is logged at error"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        context.getBean(UnhandledService).execute()

        then:
        IllegalStateException exception = thrown()
        exception.message == "unhandled"
        ILoggingEvent event = appender.list.find { it.level == Level.ERROR }
        event != null
        event.formattedMessage.contains("executed with error: unhandled")
        event.throwableProxy.className == IllegalStateException.name

        cleanup:
        context.close()
    }

    void "a failing fallback still logs the original exception at error"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        context.getBean(FailingFallbackService).execute()

        then:
        FallbackException thrownException = thrown()
        thrownException.suppressed.any { it instanceof IllegalStateException && it.message == "original" }
        ILoggingEvent event = appender.list.find { it.level == Level.ERROR && it.formattedMessage.contains("executed with error: original") }
        event != null
        event.throwableProxy.className == IllegalStateException.name

        cleanup:
        context.close()
    }

    static interface HandledApi {
        String execute()
    }

    @Singleton
    @Recoverable(api = HandledApi)
    static class HandledService implements HandledApi {

        @Override
        String execute() {
            throw new IllegalStateException("handled")
        }
    }

    @Fallback
    static class HandledFallback implements HandledApi {

        @Override
        String execute() {
            return "fallback"
        }
    }

    @Singleton
    @Recoverable
    static class UnhandledService {

        String execute() {
            throw new IllegalStateException("unhandled")
        }
    }

    static interface FailingFallbackApi {
        String execute()
    }

    @Singleton
    @Recoverable(api = FailingFallbackApi)
    static class FailingFallbackService implements FailingFallbackApi {

        @Override
        String execute() {
            throw new IllegalStateException("original")
        }
    }

    @Singleton
    @Fallback
    static class ThrowingFallback implements FailingFallbackApi {

        @Override
        String execute() {
            throw new UnsupportedOperationException("fallback blew up")
        }
    }
}
