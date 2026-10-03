package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.ConfigurationException
import spock.lang.Specification

class CloudPlatformPropertySpec extends Specification {

    void cleanup() {
        System.clearProperty(Environment.CLOUD_PLATFORM_PROPERTY)
    }

    void "an unknown cloud platform is rejected"() {
        given:
        System.setProperty(Environment.CLOUD_PLATFORM_PROPERTY, "UNKNOWN_PLATFORM")

        when:
        ApplicationContext.builder().deduceCloudEnvironment(true).build().start().close()

        then:
        def e = thrown(ConfigurationException)
        e.message == "Illegal value specified for [micronaut.cloud.platform]: UNKNOWN_PLATFORM"
    }
}
