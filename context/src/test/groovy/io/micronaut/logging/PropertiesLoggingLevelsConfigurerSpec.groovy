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
        def configurer = new PropertiesLoggingLevelsConfigurer(env, configuration, List.of(loggingSystem), context as io.micronaut.context.WatchableBeanContext)

        when: "a change under the logger prefix is refreshed: the configurer's watch applies the levels"
        context.getBean(io.micronaut.runtime.context.scope.refresh.ConfigurationRefresher).refresh(io.micronaut.context.watch.ConfigurationChange.ofKeys(["logger.levels.com.foo.bar"] as Set))

        then:
        def loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory()
        loggerContext.getLogger('com.foo.bar').level == Level.INFO
        loggerContext.getLogger('com.packass').level == Level.DEBUG
        loggerContext.getLogger('my.warn').level == Level.WARN
        loggerContext.getLogger('my.off').level == Level.OFF
    }

    void "a published refresh event applies the logger levels once, in the #environments environment"() {
        given:
        Map<String, Object> values = ["spec.name": "PropertiesLoggingLevelsConfigurerSpec.refresh", "logger.levels.fn.logger": "info"]
        def source = new io.micronaut.context.env.MapPropertySource("test", values) {
            @Override
            Object get(String key) { values[key] }

            @Override
            Iterator<String> iterator() { values.keySet().iterator() }
        }
        def context = ApplicationContext.builder().deduceEnvironment(false).environments(environments as String[]).propertySources(source).start()
        def recording = context.getBean(RecordingLoggingSystem)

        expect:
        context.containsBean(io.micronaut.runtime.context.scope.refresh.RefreshScope) == scoped
        recording.set.count("fn.logger=INFO") == 1

        when: "something refreshes the environment and publishes the event, as the refresh endpoint does"
        values["logger.levels.fn.logger"] = "debug"
        context.publishEvent(new io.micronaut.runtime.context.scope.refresh.RefreshEvent(context.environment.refreshAndDiff()))

        then: "the level is set, and once"
        recording.set.count("fn.logger=DEBUG") == 1

        and: "without a refresh scope the event refreshes nothing but the levels, as before the refresher"
        context.getBean(PropertiesLoggingLevelsConfigurer.PropertiesLoggingLevelsConfiguration).levels["fn.logger"] == (scoped ? LogLevel.DEBUG : LogLevel.INFO)

        when: "the refresher runs and publishes the event for the listeners written before it"
        values["logger.levels.fn.logger"] = "warn"
        context.getBean(io.micronaut.runtime.context.scope.refresh.ConfigurationRefresher).refresh()

        then: "the event it published does not apply the levels a second time"
        recording.set.count("fn.logger=WARN") == 1

        cleanup:
        context.close()

        where:
        environments                       | scoped
        [io.micronaut.context.env.Environment.FUNCTION] | false
        [io.micronaut.context.env.Environment.ANDROID]  | false
        []                                 | true
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
