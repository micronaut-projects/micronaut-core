package io.micronaut.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.ConsoleAppender
import com.github.stefanbirkner.systemlambda.SystemLambda
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.env.yaml.YamlPropertySourceLoader
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.context.exceptions.ConfigurationException
import org.slf4j.LoggerFactory
import spock.lang.Specification

class LogbackLogLevelConfigurerSpec extends Specification {

    void 'test that log levels on logger "#loggerName" can be configured via properties'() {
        given:
            def loggerLevels = [
                    'logger.levels.aaa.bbb.ccc'   : 'ERROR',
                    'logger.levels.foo.bar1'      : 'DEBUG',
                    'logger.levels.foo.bar2'      : 'INFO',
                    'logger.levels.foo.bar3'      : '',
                    'logger.levels.foo.barBaz'    : 'INFO',
                    'logger.levels.ignoring.error': 'OFF',
            ]

        when:
            ApplicationContext context = ApplicationContext.run(loggerLevels)

        then:
            ((Logger) LoggerFactory.getLogger(loggerName)).getLevel() == expectedLevel

        cleanup:
            context.close()

        where:
            loggerName       | expectedLevel
            'foo.bar1'       | Level.DEBUG
            'foo.bar2'       | Level.INFO
            'foo.bar3'       | null
            'aaa.bbb.ccc'    | Level.ERROR
            'foo.barBaz'     | Level.INFO
            'ignoring.error' | Level.OFF
    }

    void 'test that log level OFF without quotes does not get treated as boolean false'() {
        given:
        def map = new YamlPropertySourceLoader().read("myconfig.yml", '''
logger:
  levels:
    io.annoying.class: OFF
'''.bytes)

        when:
        ApplicationContext context = ApplicationContext.builder()
                .propertySources(PropertySource.of(map))
                .start()

        then:
        ((Logger) LoggerFactory.getLogger("io.annoying.class")).getLevel() == Level.OFF

        cleanup:
        context.close()
    }

    void 'test that log level ON throws BeanInstantiationException with nested cause of ConfigurationException'() {
        given:
        def map = new YamlPropertySourceLoader().read("myconfig.yml", '''
logger:
  levels:
    io.annoying.class: ON
'''.bytes)

        when:
        ApplicationContext.builder()
                .propertySources(PropertySource.of(map))
                .start()

        then:
        BeanInstantiationException beanInstantiationException = thrown(BeanInstantiationException)
        beanInstantiationException.cause.cause instanceof ConfigurationException
    }

    void 'test that log levels can be configured via environment variables'() {
        when:
            ApplicationContext context = ApplicationContext.builder().build()
            SystemLambda
                    .withEnvironmentVariable("LOGGER_LEVELS_FOO_BAR1", "DEBUG")
                    .and("LOGGER_LEVELS_FOO_BAR2", "INFO")
                    .execute(() -> {
                        context.start()
                    })

        then:
            ((Logger) LoggerFactory.getLogger(loggerName)).getLevel() == expectedLevel

        cleanup:
            context.close()

        where:
            loggerName    | expectedLevel
            'foo.bar1'    | Level.DEBUG
            'foo.bar2'    | Level.INFO
    }

    void 'test that log levels set in application.yaml can be overridden by environment variables'() {
        given:
            def map = new YamlPropertySourceLoader().read("application.yml", '''
logger:
  levels:
    foo.bar3: ERROR
'''.bytes)
            ((Logger) LoggerFactory.getLogger('foo.bar3')).setLevel(Level.DEBUG)

        when:
            // Use same order as application.yaml to ensure it loads before environment variable property source
            ApplicationContext context = ApplicationContext.builder()
                    .propertySources(PropertySource.of("application", map, YamlPropertySourceLoader.DEFAULT_POSITION))
                    .build()
            SystemLambda.withEnvironmentVariable("LOGGER_LEVELS_FOO_BAR3", "INFO")
                    .execute(() -> {
                        context.start()
                    })

        then:
            ((Logger) LoggerFactory.getLogger(loggerName)).getLevel() == expectedLevel

        cleanup:
            context.close()

        where:
            loggerName    | expectedLevel
            'foo.bar3'    | Level.INFO
    }

    void 'logging refresh is properly called on application start'() {
        given:
        def map = new YamlPropertySourceLoader().read("application.yml", '''
logger:
  config: logback-env-test.xml
  levels:
    foo.bar4: ERROR
'''.bytes)

        ApplicationContext context = ApplicationContext.builder()
                .propertySources(PropertySource.of("application", map, YamlPropertySourceLoader.DEFAULT_POSITION))
                .build()

        when:
        SystemLambda.withEnvironmentVariable("SOME_ENV_VAR", "FOO")
                .execute(() -> {
                    context.start()
                    LoggerFactory.getLogger("foo.bar4").error("Some error")
                })

        ConsoleAppender<ILoggingEvent> consoleAppender
        for (Logger logger : ((LoggerContext)LoggerFactory.getILoggerFactory()).getLoggerList()) {
            for (Iterator<Appender<ILoggingEvent>> index = logger.iteratorForAppenders(); index.hasNext();) {
                Appender<ILoggingEvent> appender = index.next()
                if (appender.getName() == "STDOUT") {
                    consoleAppender = (ConsoleAppender<ILoggingEvent>) appender
                    break
                }
            }
        }

        then:
        consoleAppender != null
        consoleAppender.getEncoder().getProperties()["pattern"].contains("[FOO]")

        cleanup:
        context.close()
    }

}
