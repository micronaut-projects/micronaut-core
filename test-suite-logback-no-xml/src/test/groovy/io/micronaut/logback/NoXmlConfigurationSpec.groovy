package io.micronaut.logback

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import io.micronaut.context.ApplicationContext
import org.slf4j.LoggerFactory
import spock.lang.Issue
import spock.lang.See
import spock.lang.Specification

@Issue("https://github.com/micronaut-projects/micronaut-core/issues/13390")
@See("https://logback.qos.ch/manual/configuration.html#auto_configuration")
class NoXmlConfigurationSpec extends Specification {

    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory()

    void "without any Logback XML file a logger.levels property does not fail the startup"() {
        expect: 'Logback configured itself with its basic console configuration at startup'
        rootAppenders() == ['console']

        when:
        ApplicationContext context = ApplicationContext.run([
                "logger.levels.io.micronaut": "INFO"
        ])

        then: 'the refresh also falls back to the basic console configuration'
        noExceptionThrown()
        rootAppenders() == ['console']

        and: 'custom levels are still respected'
        loggerContext.getLogger("io.micronaut").level == Level.INFO

        cleanup:
        context?.close()
    }

    private List<String> rootAppenders() {
        loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).iteratorForAppenders().collect { it.name }
    }
}
