package io.micronaut.logback

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import io.micronaut.context.annotation.Property
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import spock.lang.Specification

@MicronautTest
// Setting a level in a property forces a refresh, so the XML configuration is ignored. Without this in 3.8.x, the test fails.
@Property(name = "logger.levels.set.by.property", value = "DEBUG")
class LoggerConfigurationSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    void "if configuration is supplied, xml should be ignored"() {
        given:
        Logger fromXml = (Logger) LoggerFactory.getLogger("i.should.not.exist")
        Logger fromConfigurator = (Logger) LoggerFactory.getLogger("i.should.exist")
        Logger fromProperties = (Logger) LoggerFactory.getLogger("set.by.property")

        expect:
        fromXml.level == null
        fromConfigurator.level == Level.TRACE
        fromProperties.level == Level.DEBUG
    }
}
