package io.micronaut.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import com.github.stefanbirkner.systemlambda.SystemLambda
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.env.yaml.YamlPropertySourceLoader
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.context.exceptions.ConfigurationException
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.lang.Unroll

class LogbackLogLevelConfigurerSpec extends Specification {

    @Unroll
    void 'test that log levels on logger "#loggerName" can be configured via properties'() {
        given:
            ((Logger) LoggerFactory.getLogger('foo.bar1')).setLevel(Level.DEBUG)
            ((Logger) LoggerFactory.getLogger('foo.bar2')).setLevel(Level.DEBUG)
            ((Logger) LoggerFactory.getLogger('foo.bar3')).setLevel(Level.ERROR)
            ((Logger) LoggerFactory.getLogger('foo.barBaz')).setLevel(Level.WARN)
            ((Logger) LoggerFactory.getLogger('ignoring.error')).setLevel(Level.INFO)

        when:
            ApplicationContext context = ApplicationContext.run(
                    [
                            'logger.levels.aaa.bbb.ccc'   : 'ERROR',
                            'logger.levels.foo.bar2'      : 'INFO',
                            'logger.levels.foo.bar3'      : '',
                            'logger.levels.foo.barBaz'    : 'INFO',
                            'logger.levels.ignoring.error': 'OFF',
                    ]
            )

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
        given:
            ((Logger) LoggerFactory.getLogger('foo.bar1')).setLevel(Level.DEBUG)
            ((Logger) LoggerFactory.getLogger('foo.bar2')).setLevel(Level.DEBUG)

        when:
            ApplicationContext context = ApplicationContext.builder().build()
            SystemLambda.withEnvironmentVariable("LOGGER_LEVELS_FOO_BAR2", "INFO")
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

    void 'test that logger console pattern appender'() {
        given:
            def map = new YamlPropertySourceLoader().read("myconfig.yml", '''
logger:
  appenders:
    console:
      pattern: '%cyan(%d{HH:mm:ss.SSS}) %gray([%thread]) %highlight(%-5level) %magenta(%logger{36}) - %msg%n'
  levels:
    xyz: INFO
'''.bytes)
        when:
            def rootLogger = (Logger) LoggerFactory.getLogger("root")
        then:
            rootLogger.getAppender("console") == null

        when:
            ApplicationContext context = ApplicationContext.builder()
                    .propertySources(PropertySource.of(map))
                    .start()
            def logger = (Logger) LoggerFactory.getLogger("xyz")
        then:
            logger.getLevel() == Level.INFO
            rootLogger.getAppender("console") != null

        cleanup:
            context.close()
            ((LoggerContext) LoggerFactory.getILoggerFactory()).reset()
    }

    void 'test that logger file pattern appender'() {
        given:
            def map = new YamlPropertySourceLoader().read("myconfig.yml", '''
logger:
  appenders:
    console:
      pattern: '%cyan(%d{HH:mm:ss.SSS}) %gray([%thread]) %highlight(%-5level) %magenta(%logger{36}) - %msg%n'
    custom-console:
      pattern: '%cyan(%d{HH:mm:ss.SSS}) %gray([%thread]) %highlight(%-5level) %magenta(%logger{36}) - %msg%n'
      type: console
    file:
      file: out.log
      pattern: '%cyan(%d{HH:mm:ss.SSS}) %gray([%thread]) %highlight(%-5level) %magenta(%logger{36}) - %msg%n'
    custom-file:
      file: out2.log
      type: file
      pattern: '%cyan(%d{HH:mm:ss.SSS}) %gray([%thread]) %highlight(%-5level) %magenta(%logger{36}) - %msg%n'
  levels:
    xyz: INFO
'''.bytes)
        when:
            def rootLogger = (Logger) LoggerFactory.getLogger("root")
        then:
            rootLogger.getAppender("console") == null
            rootLogger.getAppender("file") == null

        when:
            ApplicationContext context = ApplicationContext.builder()
                    .propertySources(PropertySource.of(map))
                    .start()
        then:
            rootLogger.getAppender("console") != null
            rootLogger.getAppender("custom-console") != null
            rootLogger.getAppender("file") != null
            rootLogger.getAppender("custom-file") != null

        cleanup:
            context.close()
            ((LoggerContext) LoggerFactory.getILoggerFactory()).reset()
    }


}
