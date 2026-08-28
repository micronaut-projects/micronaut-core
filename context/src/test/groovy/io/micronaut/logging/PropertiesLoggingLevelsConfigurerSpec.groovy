package io.micronaut.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.logging.impl.LogbackLoggingSystem
import org.slf4j.LoggerFactory
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PropertiesLoggingLevelsConfigurerSpec extends Specification {
    @Shared
    Map<String, Object> config = [
            'logger.levels.com.foo.bar': 'info',
            'logger.levels.com.packass': 'DeBuG',
            'logger.levels.my.warn': 'WARN',
            'logger.levels.my.off': false
    ]
    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext
            .builder()
            .propertySources(PropertySource.of(config))
            .start()

    void "test set log level"() {
        given:
        def env = context.getEnvironment()
        def loggingSystem = new LogbackLoggingSystem(null, null)
        def configuration = context.getBean(PropertiesLoggingLevelsConfigurer.PropertiesLoggingLevelsConfiguration)
        def configurer = new PropertiesLoggingLevelsConfigurer(env, configuration, List.of(loggingSystem))

        when:
        configurer.onApplicationEvent(null)

        then:
        def loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory()
        loggerContext.getLogger('com.foo.bar').level == Level.INFO
        loggerContext.getLogger('com.packass').level == Level.DEBUG
        loggerContext.getLogger('my.warn').level == Level.WARN
        loggerContext.getLogger('my.off').level == Level.OFF
    }

    void "test configuration properties preserve raw logger names"() {
        expect:
        context.getBean(PropertiesLoggingLevelsConfigurer.PropertiesLoggingLevelsConfiguration).levels.keySet().containsAll([
                'com.foo.bar',
                'com.packass',
                'my.warn',
                'my.off'
        ])
    }
}
