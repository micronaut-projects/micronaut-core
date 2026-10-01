package io.micronaut.logging.impl

import ch.qos.logback.classic.ClassicConstants
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.Configurator
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.joran.spi.JoranException
import ch.qos.logback.core.read.ListAppender
import ch.qos.logback.core.spi.ContextAwareBase
import io.micronaut.context.ApplicationContext
import io.micronaut.logging.LoggingSystemException
import org.slf4j.LoggerFactory
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.TempDir
import spock.util.environment.RestoreSystemProperties

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/**
 * This module has no Logback XML file on the classpath, and a {@code logback.xml} in its directory, which is
 * the working directory of its tests. Logback's own lookup does not search the working directory, but the
 * refresh always has.
 * <p>
 * One spec per module on purpose: the first two features assert on the process-global Logback state, which
 * starts as whatever Logback's startup made of this module's classpath and working directory.
 * <p>
 * The spec is in this package to reach the package-private {@code LogbackUtils.configure} that takes the class
 * loader of Logback's own lookup. That makes {@code io.micronaut.logging.impl} a split package between
 * micronaut-context and this module's tests, which only works on the class path. If micronaut-context is ever
 * sealed or put on the module path, the features that call that overload have to go through the public method.
 */
@Issue("https://github.com/micronaut-projects/micronaut-core/issues/13390")
@Stepwise
class WorkingDirectoryConfigurationSpec extends Specification {

    @TempDir
    Path dir

    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory()

    void "a logback.xml that is only in the working directory is used when a logger.levels property refreshes the configuration"() {
        expect: 'Logback does not look in the working directory at startup. "console" is the appender of its BasicConfigurator'
        rootAppenders(loggerContext) == ['console']

        when:
        ApplicationContext context = ApplicationContext.run([
                "logger.levels.app.customisation": "DEBUG"
        ])

        then: 'the refresh configures Logback from the file in the working directory'
        rootAppenders(loggerContext) == ['WORKING_DIRECTORY']
        loggerContext.getLogger("from.working.directory").level == Level.TRACE

        and: 'custom levels are still respected'
        loggerContext.getLogger("app.customisation").level == Level.DEBUG

        cleanup:
        context?.close()
    }

    @RestoreSystemProperties
    void "the logback.xml in the working directory is not used when -Dlogback.configurationFile is set"() {
        given:
        System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, "missing-logback.xml")

        when:
        ApplicationContext context = ApplicationContext.run([
                "logger.levels.app.customisation": "DEBUG"
        ])

        then: 'Logback falls back to its basic console configuration, as at startup'
        rootAppenders(loggerContext) == ['console']
        loggerContext.getLogger("from.working.directory").level == null

        cleanup:
        context?.close()
    }

    void "the logback.xml in the working directory is not used when a Configurator service #description"() {
        given:
        LoggerContext freshContext = new LoggerContext()
        Path services = dir.resolve('META-INF/services')
        Files.createDirectories(services)
        Files.write(services.resolve(Configurator.name), [configurator.name])
        URLClassLoader classLoader = new URLClassLoader([dir.toUri().toURL()] as URL[], getClass().classLoader)
        CountingConfigurator.INSTANCES.set(0)

        when:
        LogbackUtils.configure(getClass().classLoader, classLoader, new File(ClassicConstants.AUTOCONFIG_FILE), freshContext, null, null)

        then: 'Logback configures the context by itself, as at startup'
        rootAppenders(freshContext) == expected

        and: 'only Logback instantiates the service: asking whether there is one does not'
        CountingConfigurator.INSTANCES.get() == 1

        cleanup:
        freshContext.stop()
        classLoader.close()

        where:
        description          | configurator          | expected
        'configures Logback' | StubConfigurator      | ['STUB']
        'declines'           | DecliningConfigurator | ['console']
    }

    void "the logback.xml in the working directory is not used when #file is on the classpath"() {
        given: 'a class loader for Logback\'s own lookup that holds the file'
        LoggerContext freshContext = new LoggerContext()
        Files.writeString(dir.resolve(file), '''\
            <configuration>
                <appender name="CLASSPATH" class="ch.qos.logback.core.read.ListAppender"/>
                <root level="info">
                    <appender-ref ref="CLASSPATH"/>
                </root>
            </configuration>
            '''.stripIndent())
        URLClassLoader classLoader = new URLClassLoader([dir.toUri().toURL()] as URL[], getClass().classLoader)

        when:
        LogbackUtils.configure(getClass().classLoader, classLoader, new File(ClassicConstants.AUTOCONFIG_FILE), freshContext, null, null)

        then: 'the file in the working directory is left alone, and Logback configures the context by itself'
        // Not CLASSPATH: DefaultJoranConfigurator searches with the class loader that loaded it, which does not
        // hold the file, so Logback ends at its BasicConfigurator. In production the two class loaders are the
        // same one. TestXmlConfigurationSpec covers that, with the files on the real classpath.
        rootAppenders(freshContext) == ['console']

        cleanup:
        freshContext.stop()
        classLoader.close()

        where:
        file << [ClassicConstants.TEST_AUTOCONFIG_FILE, ClassicConstants.AUTOCONFIG_FILE]
    }

    void "a logback.xml in the working directory that cannot be parsed still fails the refresh"() {
        given: 'the file of this module, broken for the duration of this feature'
        Path file = Path.of(ClassicConstants.AUTOCONFIG_FILE)
        byte[] original = Files.readAllBytes(file)
        Files.writeString(file, '<configuration><appender name="BROKEN"')

        when:
        ApplicationContext.run([
                "logger.levels.app.customisation": "DEBUG"
        ]).close()

        then: 'the framework reads this file itself, and it fails as it did before and as a file set in Micronaut configuration does'
        Exception e = thrown()
        LoggingSystemException cause = causes(e).find { it instanceof LoggingSystemException } as LoggingSystemException
        cause != null
        cause.message == 'Error while refreshing Logback'
        cause.cause instanceof JoranException

        cleanup:
        if (original != null) {
            Files.write(file, original)
        }
    }

    private static List<String> rootAppenders(LoggerContext context) {
        context.getLogger(Logger.ROOT_LOGGER_NAME).iteratorForAppenders().collect { it.name }
    }

    private static List<Throwable> causes(Throwable e) {
        List<Throwable> causes = []
        while (e != null && !causes.contains(e)) {
            causes << e
            e = e.cause
        }
        return causes
    }

    static abstract class CountingConfigurator extends ContextAwareBase implements Configurator {

        static final AtomicInteger INSTANCES = new AtomicInteger()

        CountingConfigurator() {
            INSTANCES.incrementAndGet()
        }
    }

    static class StubConfigurator extends CountingConfigurator {

        @Override
        ExecutionStatus configure(LoggerContext context) {
            ListAppender<ILoggingEvent> appender = new ListAppender<>()
            appender.context = context
            appender.name = 'STUB'
            appender.start()
            context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender)
            return ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY
        }
    }

    static class DecliningConfigurator extends CountingConfigurator {

        @Override
        ExecutionStatus configure(LoggerContext context) {
            return ExecutionStatus.INVOKE_NEXT_IF_ANY
        }
    }
}
