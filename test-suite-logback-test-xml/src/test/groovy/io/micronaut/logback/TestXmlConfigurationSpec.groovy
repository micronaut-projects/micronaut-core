package io.micronaut.logback

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import io.micronaut.context.ApplicationContext
import org.slf4j.LoggerFactory
import spock.lang.Issue
import spock.lang.See
import spock.lang.Specification

/**
 * One spec per module on purpose: the Logback state of the whole JVM is under test. It asserts on the
 * process-global {@link LoggerContext}, which starts as whatever Logback's startup made of this module's
 * classpath, here one with both {@code logback-test.xml} and {@code logback.xml}. A second spec in this
 * module would share that state and make the result depend on the order of the specs.
 */
@Issue("https://github.com/micronaut-projects/micronaut-core/issues/13390")
@See("https://logback.qos.ch/manual/configuration.html#auto_configuration")
class TestXmlConfigurationSpec extends Specification {

    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory()

    void "logback-test.xml still wins over logback.xml when a logger.levels property refreshes the configuration"() {
        expect: 'Logback configured itself from logback-test.xml at startup'
        rootAppenders() == ['TEST']

        when:
        ApplicationContext context = ApplicationContext.run([
                "logger.levels.app.customisation": "DEBUG"
        ])

        then: 'the refresh also configures Logback from logback-test.xml'
        rootAppenders() == ['TEST']
        loggerContext.getLogger("from.test").level == Level.TRACE
        loggerContext.getLogger("from.main").level == null

        and: 'custom levels are still respected'
        loggerContext.getLogger("app.customisation").level == Level.DEBUG

        cleanup:
        context?.close()
    }

    private List<String> rootAppenders() {
        loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).iteratorForAppenders().collect { it.name }
    }
}
