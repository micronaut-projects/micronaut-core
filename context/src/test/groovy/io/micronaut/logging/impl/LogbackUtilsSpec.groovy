package io.micronaut.logging.impl

import ch.qos.logback.classic.ClassicConstants
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.Configurator
import ch.qos.logback.classic.spi.ConfiguratorRank
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.LogbackException
import ch.qos.logback.core.joran.spi.JoranException
import ch.qos.logback.core.read.ListAppender
import ch.qos.logback.core.spi.ContextAwareBase
import io.micronaut.logging.LoggingSystemException
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir
import spock.util.environment.RestoreSystemProperties

import java.nio.file.Files
import java.nio.file.Path

/**
 * The configurator cases of the issue: a fresh {@link LoggerContext} and a class loader that holds the
 * {@link Configurator} service entry. Logback's own lookup finds this module's {@code logback.xml}, whose
 * root appender is {@code STDOUT}.
 * <p>
 * Logback's startup looks the {@link Configurator} services up with the class loader that loaded Logback,
 * and so does the public {@code configure}. These features therefore go through its package-private
 * overload, which takes that class loader. The public method differs from it by that one argument, and
 * the feature about the class loader that loaded Logback is the one that goes through it.
 */
@Issue("https://github.com/micronaut-projects/micronaut-core/issues/13390")
@RestoreSystemProperties
class LogbackUtilsSpec extends Specification {

    private static final String CUSTOM = 'custom-logback.xml'
    private static final String MISSING = 'missing-logback.xml'
    private static final String BROKEN = 'broken-logback.xml'

    @TempDir
    Path dir

    LoggerContext context = new LoggerContext()

    List<URLClassLoader> classLoaders = []

    void setup() {
        Files.writeString(dir.resolve(CUSTOM), '''\
            <configuration>
                <appender name="CUSTOM" class="ch.qos.logback.core.read.ListAppender"/>
                <root level="info">
                    <appender-ref ref="CUSTOM"/>
                </root>
            </configuration>
            '''.stripIndent())
    }

    void cleanup() {
        context.stop()
        classLoaders*.close()
    }

    void "#description"() {
        given:
        if (systemProperty != null) {
            System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, location(systemProperty))
        }

        when:
        configure(configurators, location(configurationFile), location(loggerConfig))

        then:
        rootAppenders() == expected

        where:
        // STDOUT is not Logback's doing: it is the root appender that src/test/resources/logback.xml of this
        // module declares, which Logback's own lookup finds on the classpath. A row that expects it breaks
        // if that file renames the appender.
        description                                                           | configurators                  | configurationFile | loggerConfig | systemProperty | expected
        'logger.config wins over a configurator'                              | [StubConfigurator]             | null              | CUSTOM       | null           | ['CUSTOM']
        'Joran runs after a configurator that declines'                       | [DecliningConfigurator]        | null              | null         | null           | ['STDOUT']
        'Joran runs after a configurator that returns NEUTRAL'                | [NeutralConfigurator]          | null              | null         | null           | ['STUB', 'STDOUT']
        'logback.configurationFile in configuration wins over a configurator' | [StubConfigurator]             | CUSTOM            | null         | null           | ['CUSTOM']
        'a configurator wins over -Dlogback.configurationFile'                | [StubConfigurator]             | CUSTOM            | null         | CUSTOM         | ['STUB']
        'the highest ranked configurator wins'                                | [StubConfigurator, HighRanked] | null              | null         | null           | ['HIGH']
        'Logback finds -Dlogback.configurationFile'                           | []                             | CUSTOM            | null         | CUSTOM         | ['CUSTOM']
        '-Dlogback.configurationFile wins over logger.config'                 | []                             | CUSTOM            | MISSING      | CUSTOM         | ['CUSTOM']
        'Logback falls back when -Dlogback.configurationFile is missing'      | []                             | MISSING           | null         | MISSING        | ['STDOUT']
        'logback.configurationFile in configuration wins over logger.config'  | []                             | CUSTOM            | MISSING      | null           | ['CUSTOM']
        'logback.configurationFile that overrides the JVM system property'    | [StubConfigurator]             | CUSTOM            | null         | MISSING        | ['CUSTOM']
    }

    void "Logback resolves a -Dlogback.configurationFile that is a URL"() {
        given:
        String url = dir.resolve(CUSTOM).toUri().toString()
        System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, url)

        when:
        configure([], url, null)

        then:
        rootAppenders() == ['CUSTOM']
    }

    void "the public method looks the Configurator services up with the class loader that loaded Logback"() {
        when: 'the class loader for the locations of the Micronaut configuration holds a Configurator service'
        LogbackUtils.configure(classLoader([StubConfigurator]), context, null, null)

        then: 'Logback never sees it, as its startup did not'
        rootAppenders() == ['STDOUT']
    }

    void "a Configurator service that #description fails with a LoggingSystemException"() {
        when:
        LogbackUtils.configure(getClass().classLoader, classLoader(services), context, null, null)

        then:
        LoggingSystemException e = thrown()
        e.message == 'Error while refreshing Logback'
        cause.isInstance(e.cause)

        where:
        description        | services                                         | cause
        'throws'           | [ThrowingConfigurator]                           | LogbackException
        'cannot be loaded' | ['io.micronaut.logging.impl.NoSuchConfigurator'] | ServiceConfigurationError
    }

    void "a missing location set only in Micronaut configuration fails, whatever configurators exist"() {
        when:
        configure([StubConfigurator], configurationFile, loggerConfig)

        then:
        LoggingSystemException e = thrown()
        e.message == "Resource $MISSING not found"

        where:
        configurationFile | loggerConfig
        MISSING           | null
        null              | MISSING
    }

    void "a location set only in Micronaut configuration that cannot be parsed fails"() {
        given:
        Files.writeString(dir.resolve(BROKEN), '<configuration><appender name="BROKEN"')

        when:
        configure([], location(configurationFile), location(loggerConfig))

        then:
        LoggingSystemException e = thrown()
        e.message == 'Error while refreshing Logback'
        e.cause instanceof JoranException

        where:
        configurationFile | loggerConfig
        BROKEN            | null
        null              | BROKEN
    }

    private String location(String name) {
        name == null ? null : dir.resolve(name).toString()
    }

    private void configure(List<Class<? extends Configurator>> configurators, String configurationFile, String loggerConfig) {
        LogbackUtils.configure(getClass().classLoader, classLoader(configurators), context, configurationFile, loggerConfig)
    }

    /**
     * @param configurators The {@link Configurator} classes, or their names, to register as services
     */
    private ClassLoader classLoader(List<?> configurators) {
        Path services = dir.resolve('META-INF/services')
        Files.createDirectories(services)
        Files.write(services.resolve(Configurator.name), configurators.collect { it instanceof Class ? it.name : it as String })
        URLClassLoader classLoader = new URLClassLoader([dir.toUri().toURL()] as URL[], getClass().classLoader)
        classLoaders << classLoader
        return classLoader
    }

    private List<String> rootAppenders() {
        context.getLogger(Logger.ROOT_LOGGER_NAME).iteratorForAppenders().collect { it.name }
    }

    static abstract class AbstractStubConfigurator extends ContextAwareBase implements Configurator {

        private final String appenderName
        private final ExecutionStatus status

        AbstractStubConfigurator(String appenderName, ExecutionStatus status) {
            this.appenderName = appenderName
            this.status = status
        }

        @Override
        ExecutionStatus configure(LoggerContext loggerContext) {
            if (appenderName != null) {
                ListAppender<ILoggingEvent> appender = new ListAppender<>()
                appender.context = loggerContext
                appender.name = appenderName
                appender.start()
                loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender)
            }
            return status
        }
    }

    static class StubConfigurator extends AbstractStubConfigurator {
        StubConfigurator() {
            super('STUB', ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY)
        }
    }

    static class NeutralConfigurator extends AbstractStubConfigurator {
        NeutralConfigurator() {
            super('STUB', ExecutionStatus.NEUTRAL)
        }
    }

    static class DecliningConfigurator extends AbstractStubConfigurator {
        DecliningConfigurator() {
            super(null, ExecutionStatus.INVOKE_NEXT_IF_ANY)
        }
    }

    static class ThrowingConfigurator extends AbstractStubConfigurator {
        ThrowingConfigurator() {
            super(null, ExecutionStatus.NEUTRAL)
        }

        @Override
        ExecutionStatus configure(LoggerContext loggerContext) {
            throw new IllegalStateException('broken')
        }
    }

    @ConfiguratorRank(ConfiguratorRank.CUSTOM_HIGH_PRIORITY)
    static class HighRanked extends AbstractStubConfigurator {
        HighRanked() {
            super('HIGH', ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY)
        }
    }
}
