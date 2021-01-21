package io.micronaut.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.github.stefanbirkner.systemlambda.SystemLambda
import io.micronaut.context.ApplicationContext
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

        when:
            ApplicationContext context = ApplicationContext.run(
                    [
                            'logger.levels.aaa.bbb.ccc': 'ERROR',
                            'logger.levels.foo.bar2'   : 'INFO',
                            'logger.levels.foo.bar3'   : '',
                            'logger.levels.foo.barBaz'   : 'INFO',
                    ]
            )

        then:
            ((Logger) LoggerFactory.getLogger(loggerName)).getLevel() == expectedLevel

        cleanup:
            context.close()

        where:
            loggerName    | expectedLevel
            'foo.bar1'    | Level.DEBUG
            'foo.bar2'    | Level.INFO
            'foo.bar3'    | null
            'aaa.bbb.ccc' | Level.ERROR
            'foo.barBaz'    | Level.INFO

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

}
