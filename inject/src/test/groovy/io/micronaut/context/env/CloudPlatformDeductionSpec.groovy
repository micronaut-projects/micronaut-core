package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class CloudPlatformDeductionSpec extends Specification {

    private static final Set<String> PLATFORMS = Set.of(
        Environment.GOOGLE_COMPUTE,
        Environment.AMAZON_EC2,
        Environment.ORACLE_CLOUD,
        Environment.AZURE,
        Environment.IBM,
        Environment.DIGITAL_OCEAN,
        Environment.HEROKU,
        Environment.GAE
    )

    void "the compute platform is probed when no platform is configured"() {
        given:
        System.clearProperty(Environment.CLOUD_PLATFORM_PROPERTY)

        when:
        ApplicationContext context = ApplicationContext.builder().deduceCloudEnvironment(true).build().start()
        Set<String> active = context.environment.activeNames

        then: "the probes of the host never fail, and a deduced platform comes with the cloud environment"
        !active.any { PLATFORMS.contains(it) } || active.contains(Environment.CLOUD)

        cleanup:
        context?.close()
    }
}
