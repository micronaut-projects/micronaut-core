package io.micronaut.logging.impl

import ch.qos.logback.classic.ClassicConstants
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.Configurator
import ch.qos.logback.core.status.ErrorStatus
import spock.lang.Specification
import spock.lang.TempDir
import spock.util.environment.RestoreSystemProperties

import java.nio.file.Files
import java.nio.file.Path

/**
 * Development mode's configuration of Logback from the application's resources, which are on a class loader of
 * their own that Logback's lookup does not search.
 */
@RestoreSystemProperties
class LogbackResourcesSpec extends Specification {

    @TempDir
    Path dir

    LoggerContext context = new LoggerContext()

    List<URLClassLoader> classLoaders = []

    void cleanup() {
        context.stop()
        classLoaders*.close()
    }

    void "the configuration file among the application's resources configures Logback"() {
        given:
        write(ClassicConstants.AUTOCONFIG_FILE, 'RESOURCES')

        when:
        LogbackUtils.configure(getClass().classLoader, resources(), context, null, null)

        then:
        rootAppenders() == ['RESOURCES']
    }

    void "a location set only in Micronaut configuration is looked up among the application's resources"() {
        given:
        write('custom-logback.xml', 'CUSTOM')
        write(ClassicConstants.AUTOCONFIG_FILE, 'RESOURCES')

        when:
        LogbackUtils.configure(getClass().classLoader, resources(), context, 'custom-logback.xml', null)

        then:
        rootAppenders() == ['CUSTOM']
    }

    void "without a loader of its own the resources are Logback's"() {
        when:
        LogbackUtils.configure(getClass().classLoader, null, context, null, null)

        then: 'the logback.xml of this module'
        rootAppenders() == ['STDOUT']
    }

    void "a reconfiguration replaces what the context had with the test configuration of the resources"() {
        given:
        write(ClassicConstants.AUTOCONFIG_FILE, 'MAIN')
        ClassLoader resources = resources()
        LogbackUtils.reconfigure(context, resources)
        assert rootAppenders() == ['MAIN']
        write(ClassicConstants.TEST_AUTOCONFIG_FILE, 'TEST')

        when:
        LogbackUtils.reconfigure(context, resources)

        then:
        rootAppenders() == ['TEST']
        LogbackUtils.describeConfiguration(resources).endsWith(ClassicConstants.TEST_AUTOCONFIG_FILE)
    }

    void "a configuration file that cannot be parsed is reported as a status, not thrown"() {
        given:
        Files.writeString(dir.resolve(ClassicConstants.AUTOCONFIG_FILE), '<configuration><appender')

        when:
        LogbackUtils.reconfigure(context, resources())

        then:
        context.statusManager.copyOfStatusList.any { it instanceof ErrorStatus && it.message.startsWith('Failed to configure Logback from') }
    }

    void "without any configuration among the resources Logback configures itself as at startup"() {
        given:
        ClassLoader resources = resources()

        expect:
        LogbackUtils.describeConfiguration(resources) == null

        when:
        LogbackUtils.reconfigure(context, resources)

        then: 'its own lookup, which finds the logback.xml of this module'
        rootAppenders() == ['STDOUT']
    }

    void "a Configurator service of the resources configures Logback"() {
        given:
        ClassLoader resources = resources([LogbackUtilsSpec.StubConfigurator])

        expect:
        LogbackUtils.describeConfiguration(resources) == LogbackUtils.CONFIGURATOR_SERVICES

        when:
        LogbackUtils.reconfigure(context, resources)

        then:
        rootAppenders() == ['STUB']
    }

    void "the logback.configurationFile system property is a URL, a resource or a file"() {
        given:
        Path file = write('elsewhere.xml', 'FILE')
        write('named.xml', 'NAMED')
        write(ClassicConstants.AUTOCONFIG_FILE, 'MAIN')
        ClassLoader resources = resources()

        when:
        System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, file.toUri().toString())

        then:
        LogbackUtils.describeConfiguration(resources) == file.toUri().toURL().toExternalForm()

        when:
        System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, 'named.xml')

        then:
        LogbackUtils.describeConfiguration(resources).endsWith('named.xml')

        when:
        System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, file.toString())

        then:
        LogbackUtils.describeConfiguration(resources) == file.toUri().toURL().toExternalForm()

        when: 'nothing of that name: the default files'
        System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, 'missing.xml')

        then:
        LogbackUtils.describeConfiguration(resources).endsWith(ClassicConstants.AUTOCONFIG_FILE)
    }

    private Path write(String name, String appender) {
        Files.writeString(dir.resolve(name), """\
            <configuration>
                <appender name="${appender}" class="ch.qos.logback.core.read.ListAppender"/>
                <root level="info">
                    <appender-ref ref="${appender}"/>
                </root>
            </configuration>
            """.stripIndent())
    }

    /**
     * The application's resources: the temporary directory, with this module's logback.xml hidden.
     */
    private ClassLoader resources(List<Class<?>> configurators = []) {
        if (configurators) {
            Path services = Files.createDirectories(dir.resolve('META-INF/services'))
            Files.write(services.resolve(Configurator.name), configurators*.name)
        }
        URLClassLoader classLoader = new LogbackUtilsSpec.NoDefaultFilesClassLoader([dir.toUri().toURL()] as URL[], getClass().classLoader)
        classLoaders << classLoader
        return classLoader
    }

    private List<String> rootAppenders() {
        context.getLogger(Logger.ROOT_LOGGER_NAME).iteratorForAppenders().collect { it.name }
    }
}
