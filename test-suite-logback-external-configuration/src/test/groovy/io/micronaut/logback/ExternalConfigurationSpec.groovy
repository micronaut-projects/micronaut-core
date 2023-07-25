package io.micronaut.logback

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import io.micronaut.context.ApplicationContext
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
    def "should still use the external config if custom levels are defines"() {
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
}
