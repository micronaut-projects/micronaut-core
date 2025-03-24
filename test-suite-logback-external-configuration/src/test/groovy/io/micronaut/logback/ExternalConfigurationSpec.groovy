package io.micronaut.logback

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.runtime.server.EmbeddedServer
import org.slf4j.LoggerFactory
import spock.lang.See
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

@See("https://logback.qos.ch/manual/configuration.html#auto_configuration")
class ExternalConfigurationSpec extends Specification {

    @RestoreSystemProperties
    def "should use the external configuration"() {
        given:
        System.setProperty("logback.configurationFile", "src/external/external-logback.xml")

        when:
        Logger fromXml = (Logger) LoggerFactory.getLogger("i.should.not.exist")
        Logger external = (Logger) LoggerFactory.getLogger("external.logging")

        then: 'logback.xml is ignored as we have set a configurationFile'
        fromXml.level == null

        and: 'external configuration is used'
        external.level == Level.TRACE
    }

    @RestoreSystemProperties
    def "should still use the external config if custom levels are defined"() {
        given:
        System.setProperty("logback.configurationFile", "src/external/external-logback.xml")

        when:
        def server = ApplicationContext.run(EmbeddedServer, [
                "logger.levels.app.customisation": "DEBUG"
        ])
        Logger fromXml = (Logger) LoggerFactory.getLogger("i.should.not.exist")
        Logger custom = (Logger) LoggerFactory.getLogger("app.customisation")
        Logger external = (Logger) LoggerFactory.getLogger("external.logging")

        then: 'logback.xml is ignored as we have set a configurationFile'
        fromXml.level == null

        and: 'custom levels are still respected'
        custom.level == Level.DEBUG

        and: 'external configuration is used'
        external.level == Level.TRACE

        cleanup:
        server.stop()
    }

    def "configuration via logger.config should work without configuring levels"() {
        when:
        def server = ApplicationContext.run(EmbeddedServer, [
                "logger.config": "src/external/external-logback.xml",
        ])
        Logger fromXml = (Logger) LoggerFactory.getLogger("i.should.not.exist")
        Logger external = (Logger) LoggerFactory.getLogger("external.logging")

        then: 'logback.xml is ignored as we have set a configurationFile'
        fromXml.level == null

        and: 'external configuration is used'
        external.level == Level.TRACE

        cleanup:
        server.stop()
    }

    void 'nonexistent logger.config file'() {
        when:
        PrintStream realSystemOut = System.out
        var out = new ByteArrayOutputStream()
        System.out = new PrintStream(out)

        PrintStream realSystemErr = System.err
        var err = new ByteArrayOutputStream()
        System.err = new PrintStream(err)

        ApplicationContext.run(EmbeddedServer, [
                'logger.config': 'doesnotexist.xml',
        ])

        then:
        thrown(BeanInstantiationException)
        err.toString().trim() == 'ERROR: Logback configuration file doesnotexist.xml not found'

        and: 'the exception is not printed to stdout or stderr'
        !out.toString().trim().contains('Caused by: io.micronaut.logging.LoggingSystemException: Resource doesnotexist.xml not found')
        !err.toString().trim().contains('Caused by: io.micronaut.logging.LoggingSystemException: Resource doesnotexist.xml not found')

        cleanup:
        System.out = realSystemOut
        System.err = realSystemErr
    }
}
